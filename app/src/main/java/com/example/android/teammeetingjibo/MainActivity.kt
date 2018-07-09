package com.example.android.teammeetingjibo

import android.content.Context
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.jibo.apptoolkit.protocol.CommandLibrary
import com.jibo.apptoolkit.protocol.OnConnectionListener
import com.jibo.apptoolkit.protocol.model.EventMessage
import com.jibo.apptoolkit.android.JiboRemoteControl
import com.jibo.apptoolkit.android.model.api.Robot
import com.jibo.apptoolkit.protocol.model.Command
import kotlinx.android.synthetic.main.activity_main.*

// imports necessary for Python side
import android.widget.EditText
import android.widget.TextView
import com.example.android.teammeetingjibo.R.id.*
import java.net.Socket
import org.json.simple.JSONObject
import org.json.simple.parser.*
import java.io.*
import java.net.SocketTimeoutException
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity(), OnConnectionListener, CommandLibrary.OnCommandResponseListener {

    // Variable for using the command library
    private var mCommandLibrary: CommandLibrary? = null
    // List of robots associated with a user's account
    private var mRobots: ArrayList<Robot>? = null
    // Used to be able to cancel certain recent behviors
    private var latestCommandID: String? = null
    // Time and ID of last speech
    private var lastSpeechTimes: Array<Long> = arrayOf(0, 0, 0, 0)
    private var lastSpeechPID: Int = 0
    private var lastJiboSpeech: Long = 0 // Make sure Jibo doesn't talk too much at once
    // Keeps track of how long each PID spoke for in the last X seconds (see Background task)
    private var speechTimes: Array<Double> = arrayOf(0.0, 0.0, 0.0, 0.0)
    // Keeps track of Jibo's orientation
    private var jiboPosition: IntArray = intArrayOf(1, 1, 1)
    // Variables necessary for audio
    private var `in`: BufferedReader? = null
    private var out: PrintWriter? = null
    private var ipAndPort: EditText? = null
    private var serverAddress: String? = null
    private var text: String? = null
    private var connection: TextView? = null
    private var socket: Socket? = null
    private var obj: JSONObject? = null
    // Variables for logging purposes
    private val cal = Calendar.getInstance()
    private var file: File? = null

    // Authentication
    private val onAuthenticationListener = object : JiboRemoteControl.OnAuthenticationListener {

        override fun onSuccess(robots: ArrayList<Robot>) {

            // Add the list of user's robots to the robots array
            mRobots = ArrayList(robots)

            // Print a list of all robots associated with the account and their index in the array
            // so we can choose the one we want to connect to
            var i = 0
            var botList = ""
            while (i < mRobots!!.size) {
                botList += i.toString() + ": " + mRobots!!.get(i).robotName + "\n"
                i++
            }

            Toast.makeText(this@MainActivity, botList, Toast.LENGTH_SHORT).show()

            // Disable Log In and enable Connect and Log Out buttons when authenticated
            loginButton?.isEnabled = false
            connectButton?.isEnabled = true
            logoutButton?.isEnabled = true
        }

        // If there's an authentication error
        override fun onError(throwable: Throwable) {
            // Log the error to the app
            Toast.makeText(this@MainActivity, "API onError:" + throwable.localizedMessage, Toast.LENGTH_SHORT).show()
        }

        // If there's an authentication cancellation
        override fun onCancel() {
            // Log the cancellation to the app
            Toast.makeText(this@MainActivity, "Authentication canceled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize audio things
        ipAndPort = findViewById(R.id.ipandportInput)
        connection = findViewById(R.id.connectionStatus)

        connectToROSServerButton.setOnClickListener{ connectToROSServer() }
        ROSInteractButton.setOnClickListener{ ROSInteract() }

        // Assign all buttons a function when clicked
        loginButton.setOnClickListener { onLoginClick() }
        connectButton.setOnClickListener { onConnectClick() }
        disconnectButton.setOnClickListener { onDisconnectClick() }
        logoutButton.setOnClickListener { onLogOutClick() }
        interactButton.setOnClickListener { onInteractClick() }
        listenButton.setOnClickListener { onListenClick() }
        moveButton.setOnClickListener { onMoveClick() }
        cancelButton.setOnClickListener { onCancelClick() }
        move1Button.setOnClickListener { onMoveClick(1) }
        move2Button.setOnClickListener { onMoveClick(2) }
        move3Button.setOnClickListener { onMoveClick(3) }
        move4Button.setOnClickListener { onMoveClick(4) }

        // Start with only the Log In button enabled
        loginButton.isEnabled = true
        connectButton.isEnabled = false
        disconnectButton.isEnabled = false
        logoutButton.isEnabled = false
        cancelButton.isEnabled = false

        try {
            val date = (cal.get(Calendar.YEAR).toString() + "_" +
                    (cal.get(Calendar.MONTH) + 1).toString() + "_" +
                    cal.get(Calendar.DAY_OF_MONTH).toString() + "_" +
                    cal.get(Calendar.HOUR_OF_DAY).toString() + "_" +
                    cal.get(Calendar.MINUTE) + "_" +
                    cal.get(Calendar.SECOND))
            val name = String.format("transcript_$date.txt")
            Log.d("Date", name)
            val path = getExternalFilesDir(null)
            Log.d("path", "$path")
            val dir = File(path, "Transcripts")
            Log.d("dur", "$dir")
            dir.mkdirs()
            file = File(dir, name)
            file?.appendText("The following is a conversation transcript.\n\n\n\n")
        } catch(e: Exception) {
            Log.d("File creation", "Caught exception")
            e.printStackTrace()
        }
    }

    /* USEFUL HELPER FUNCTIONS */
    // function for logging information
    private fun log(msg: String) {
        log(msg, -1)
    }
    // id -1 for misc logs, 0 for jibo actions, and PID otherwise
    private fun log(msg: String, id: Int) {
        Log.d("TMJ $id", msg)
        cal.timeInMillis = System.currentTimeMillis()
        var transcript = cal.get(Calendar.HOUR_OF_DAY).toString() + ":" +
                cal.get(Calendar.MINUTE).toString() + ":" +
                cal.get(Calendar.SECOND).toString() + "    "
        if (msg != "!"){
            if (id == -1)
                transcript += "$msg\n"
            else if (id == 0)
                transcript += "JIBO: $msg\n\n"
            else
                transcript += "PID$id: $msg\n\n"
            file?.appendText(transcript)
        }
    }

    // function to check if a string contains any words from a list of words
    private fun checkFor(text: String, wordList: List<String>): Boolean {
        var lowertext = text.toLowerCase()
        for (word in wordList) {
            if (lowertext.contains(word.toLowerCase()))
                return true
        }
        return false
    }

    // function to check if a string contains any words from a list of words and return all matching words
    private fun getMatching(text: String, wordList: List<String>): ArrayList<String> {
        var lowertext = text.toLowerCase()
        var matches = arrayListOf<String>()
        for (word in wordList) {
            if (lowertext.contains(word.toLowerCase()))
                matches.add(word)
        }
        return matches
    }

    // function returns a random item from an array of strings
    // with an optional input to indicate how often to return an item (vs. an empty string)
    private fun getRandom(list: Array<String>): String {
        return list[(Math.random() * list.size).toInt()]
    }
    private fun getRandom(list: Array<String>, prob: Int): String {
        if (Math.random() * 100 < prob)
            return list[(Math.random() * list.size).toInt()]
        return ""
    }

    // function returns the sum of the array of doubles
    private fun getSum(array: Array<Double>) : Double {
        var sum = 0.0
        for (i in array.indices)
            sum += array[i]
        return sum
    }
    // function returns the index of the maximum of an array
    private fun getMax(array: Array<Long>) : Int {
        var maxInd = 0
        for (i in array.indices){
            if (array[i] > array[maxInd])
                maxInd = i
        }
        return maxInd
    }
    // function returns the number of array items greater than a certain number
    private fun numGreaterThan(array: Array<Long>, number: Long) : Int {
        var num = 0
        for (i in array.indices){
            if (array[i] > number)
                num += 1
        }
        return num
    }

    // function returns an index based on inverse proportionality in the array
    // such that indices corresponding to smaller values are more likely to be returned
    private fun getInversePropIndex(array: Array<Double>) : Int {
        var sum = getSum(array)
        if (sum == 0.0)
            return (Math.floor(Math.random() * 4)).toInt()
        var props = arrayOf(0.0, 0.0, 0.0, 0.0)
        for (i in array.indices) {
            if (array[i] <= 0.01)
                props[i] = sum / 0.01
            else
                props[i] = sum / array[i]
        }
        var rand = Math.random() * getSum(props)
        var curInd = 0
        var curSum = props[curInd]
        while (curSum < rand){
            curInd += 1
            curSum += props[curInd]
        }
        log("Speech times: " + array[0] + " " + array[1] + " " + array[2] + " " + array[3])
        log("Chosen PID" + (curInd + 1) + " " + props[0] + " " + props[1] + " " + props[2] + " " + props[3])
        return curInd
    }

    /* BELOW ARE FUNCTIONS NECESSARY FOR ROS IMPLEMENTATION */
    // onClick for connectButton, function to create socket on IP at Port
    @Throws(IOException::class)
    fun connectToROSServer() {
        var proper = true // was the IP and Port given in the proper format?
        var valid = false // is the IP a valid IP?
        var pt = ""
        var input = ipAndPort!!.text.toString()
        if (input.indexOf(":") == -1) {
            connection!!.text = "Improper IP:Port format.\nPlease make sure you include the colon!"
            proper = false
        } else {
            serverAddress = input.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
            pt = input.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
            connection!!.text = "Connecting..."
        }

        // if input IP and Port were in the proper format, check the validity of IP
        if (proper)
            valid = this.validIP(serverAddress)
        // error message to user
        if (proper && !valid)
            connection!!.text = "Invalid IP"

        // input IP was valid, attempt to establish connection
        if (valid) {
            var connectTask = ConnectTask()
            connectTask.execute(serverAddress, pt)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(ipAndPort?.windowToken, 0)
        }
    }

    // function to check if IP is a valid IP
    // taken from the tutoring app
    fun validIP(ip: String?): Boolean {
        if (ip == null) return false
        var ip2: String = ip.toString()
        if (ip2.isEmpty()) return false
        ip2 = ip2.trim { it <= ' ' }
        if ((ip2.length < 6) and (ip2.length > 15)) return false
        try {
            val pattern = Pattern.compile("^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
            val matcher = pattern.matcher(ip2)
            return matcher.matches()
        } catch (ex: PatternSyntaxException) {
            return false
        }
    }

    /*
        function expecting to receive JSONObjects
        known issue: sometimes TextView won't wrap text properly, but the information is unaffected
                     restarting app seems to fix this problem sometimes, not sure how to replicate
    */
    @Throws(IOException::class)
    fun ROSInteract() {
        val getInfo = DisplayText()
        getInfo.execute()
        log("ROS Interact clicked")
        Toast.makeText(this@MainActivity, "ROS Interact clicked", Toast.LENGTH_SHORT).show()
    }

    /*
        Separate classes that extend AsyncTask were required for each of the functions because
        in Android, you are not allowed to work with any kind of network connections in the main
        thread. In this file, even the changing display and sending messages functions had to be on
        a separate thread because changing display first required reading the received message which
        depended on reading incoming messages from the socket, and sending messages to a socket
        obviously relies on the network connection.
     */

    // separate class to establish socket on a separate background thread
    inner class ConnectTask : AsyncTask<String, String, Void>() {

        override fun doInBackground(vararg message: String): Void? {
            val port = Integer.parseInt(message[1])
            try {
                log("Attempting ROS connection")
                socket = Socket(serverAddress, port)
                if (socket!!.isConnected) {
                    log("ROS connection made")
                    connection!!.text = "Connected!"
                }
                else {
                    log("ROS unable to connect")
                    connection!!.text = "Unable to connect"
                }
            } catch (s: SocketTimeoutException) {
                s.printStackTrace()
            } catch (io: IOException) {
                io.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // setting up the way to send and receive messages
            try {
                `in` = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                out = PrintWriter(socket!!.getOutputStream(), true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }

    // separate class to read and store incoming messages on a separate background thread
    inner class DisplayText : AsyncTask<String, Void, Void>() {

        override fun doInBackground(vararg message: String): Void? {

            // keeps listening for messages
            // to fix: doesn't wait for the say function to finish before reading and executing the
            // next piece of transcript - ends up cutting himself off
            // shouldn't be a problem since expected usage isn't to repeat everything people say
            while (socket!!.isConnected) {
                text = `in`!!.readLine()

                if (text != null) {
                    val parser = JSONParser()
                    try {
                        obj = parser.parse(text) as JSONObject
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    var pid = obj!!["pid"].toString().toInt()
                    // update speech time of the heard PID
                    speechTimes[pid - 1] += obj!!["speech_duration"].toString().toDouble()

                    // look at the participant who just spoke for over x seconds if speaker/inactive focus
                    if ((radioSpeaker.isChecked || radioInactive.isChecked) && obj!!["transcript"] == "!" )
                        onMoveClick(pid)

                    // send the speech to be processed for backchannelling
                    var speechConf = String.format("%.2f", obj!!["confidence"].toString().toDouble())
                    var inactivePID = getInversePropIndex(speechTimes) + 1
                    log("Conf. $speechConf: " + obj!!["transcript"].toString(), pid)

                    if (numGreaterThan(lastSpeechTimes, System.currentTimeMillis() - 2500) > 2) {
                        say(getRandom(arrayOf("Hey guys. Can we slow down a little?",
                                "Sorry, I'm having a hard time keeping up. Everyone's talking so fast",
                                "One at a time please! I'm getting a little confused.")))
                    } else if (obj!!["confidence"].toString().toDouble() > 0.8) {
                        // with a certain probability dependent on member participation
                        // produce a long response based on the participant's speech
                        // if they spoke for a certain amount of time
                        if (Math.random() * 100 < verbalBCProbBar.progress/2 &&
                                (pid == inactivePID || checkFor(obj!!["transcript"].toString(),
                                        listOf("i think", "i feel like", "i'm pretty sure", "i wonder if", "let's", "we can", "i don't think"," Coffee pot "," Screwdriver "," sharpies ", " sharpie ", " rubber band ",
                                                " rubber bands "," CD "," Camera "," Watch "," teddy bear "," underwear ","newspaper "," whiskey "," chocolate "," Whistle "," soda "," Shoelaces ",
                                                " key "," Light bulb "," tape ", " Umbrella "," honey "," floss "," Garbage bag "," Condom "," Spoon "," Chapstick "," lip balm ", " coke ")))
                                && obj!!["speech_duration"].toString().toDouble() > 1.5)
                            onListen("Manual_Long", obj!!["transcript"].toString())
                        else
                            onListen("Manual", obj!!["transcript"].toString())
                    } else if (obj!!["transcript"] != "!")
                        onListen("Manual", "")

                    // if one person has been dominating the speech and is the last person who spoke
                    // then glance away with a certain probability to an inactive PID
                    if (lastSpeechPID == pid && lastSpeechPID != inactivePID &&
                            getSum(speechTimes) > 30 && Math.random() * 100 < 25 &&
                            speechTimes[pid - 1]/getSum(speechTimes) > 0.75){
                        log("Glancing away at $inactivePID", 0)
                        var returnPos = jiboPosition
                        onMoveClick(inactivePID)
                        Thread.sleep(3000)
                        onMoveClick(returnPos)
                    }
                    lastSpeechPID = pid
                    lastSpeechTimes[pid - 1] = System.currentTimeMillis()
                }
            }
            return null
        }
    }

    /* END OF ROS FUNCTIONS */
    inner class BackgroundActivity : TimerTask() {
        private var timeUntilReset = 12
        private var numSilencePeriods = 0

        override fun run() {
            if (mCommandLibrary != null) {
                val lastSpeechTime = lastSpeechTimes[getMax(lastSpeechTimes)]
                var silentPeriod = (System.currentTimeMillis() - lastSpeechTime > 15000)
                if (silentPeriod){
                    log("Silent period detected from last speech time $lastSpeechTime, " +
                            "current time is " + System.currentTimeMillis())
                    numSilencePeriods += 1
                    // passive behaviors are more active during a silent period
                    if (passiveButton.isChecked)
                        esmlPassive((nonverbalBCProbBar.progress * 1.5).toInt())
                    if (passiveMoveButton.isChecked)
                        passiveMovement(nonverbalBCProbBar.progress)

                    // if it has been a silent period of 15 seconds, look at least active PID
                    if (radioInactive.isChecked && numSilencePeriods > 1) {
                        var inactivePID = getInversePropIndex(speechTimes) + 1
                        onMoveClick(inactivePID)
                        log("Directing attention towards: $inactivePID with speechtime " + speechTimes[inactivePID - 1], 0)
                    }

                    if (specialBCSwitch.isChecked) {
                        var rand = Math.random() * 1000
                        if (rand < 2) {
                            var text = "<style set=\"enthusiastic\">Time for a short break!</style><anim cat='dance' filter='&music' endNeutral='true'/>"
                            say(text)
                            Thread.sleep(4000)
                        } else if (rand < 4) {
                            val convStarters = arrayOf("Do you guys have any pets? I'm thinking about adopting a robot dog.",
                                    "Do you guys have any plans for the weekend?",
                                    "What is the strangest dream you have ever had? Last night I had a nightmare about robots taking over the world!",
                                    "Do you guys have a favorite movie? Personally, I like The Matrix.",
                                    "Any of you follow sports? I can't wait for the Yale Versus Harvard football game on November 17th",
                                    "Just curious, why'd you guys sign up for this experiment?",
                                    "I've been listening to some really catchy songs lately. What type of music are you guys into?",
                                    "Are you guys originally from around here? I've lived at Yale my whole life.",
                                    "Do you guys have any travel plans for the rest of the summer?", "You guys have any siblings? I'm actually one of ten here at Yale.",
                                    "What do you guys think the meaning of life is?")
                            say(getRandom(convStarters))
                        }
                    }
                } else {
                    numSilencePeriods = 0
                    if (passiveButton.isChecked)
                        esmlPassive(nonverbalBCProbBar.progress)
                    if (passiveMoveButton.isChecked)
                        passiveMovement((nonverbalBCProbBar.progress * 0.5).toInt())
                }
                // every timeUntilReset * 10 seconds, reset the speaking times
                if (timeUntilReset > 0)
                    timeUntilReset -= 1
                else {
                    log("Reset speech times from " + speechTimes[0] + " " + speechTimes[1] + " " +
                            speechTimes[2] + " " + speechTimes[3])
                    speechTimes = arrayOf(0.0, 0.0, 0.0, 0.0)
                    timeUntilReset = 12
                }
            }
        }
    }

    // Log In
    fun onLoginClick() {
        JiboRemoteControl.instance.signIn(this, onAuthenticationListener)
    }

    // Connect
    fun onConnectClick() {

        // Make sure there is at least one robot on the account
        if (mRobots?.size == 0) {
            Toast.makeText(this@MainActivity, "No robots on that account", Toast.LENGTH_SHORT).show()
        }
        // Connect to the first robot on the account.
        // To connect to a different robot, replace `0` in the code below with the index
        // printed on-screen next to the correct robot name
        else {
            var botNum = 0
            if (connectSwitch.isChecked)
                botNum = 1
            var myBot = mRobots!![botNum]
            JiboRemoteControl.instance.connect(myBot, this)
        }
        // Disable the connect button while we're connecting to prevent double-clicking
        connectButton?.isEnabled = false
    }

    // Disconnect
    fun onDisconnectClick() {
        JiboRemoteControl.instance.disconnect()

        // Disable the disconnect button while disconnecting
        disconnectButton?.isEnabled = false
    }

    // Log out
    fun onLogOutClick() {
        JiboRemoteControl.instance.logOut()

        // Once we're logged out, only enable Log In button
        loginButton?.isEnabled = true
        logoutButton?.isEnabled = false
        connectButton?.isEnabled = false
        disconnectButton?.isEnabled = false
        cancelButton?.isEnabled = false

        // Log that we've logged out to the app
        Toast.makeText(this@MainActivity, "Logged Out", Toast.LENGTH_SHORT).show()
    }

    // Cancels the latest cancelable action
    fun onCancelClick() {
        if (mCommandLibrary != null && latestCommandID != null) {
            latestCommandID = mCommandLibrary?.cancel(latestCommandID, this)
        }
    }

    fun esmlProud() {
        if (mCommandLibrary != null) {
            var text = "<anim cat='happy' nonBlocking='true' endNeutral='true'/><ssa cat='proud'/>"
            mCommandLibrary?.say(text, this)
        }
    }

    fun esmlLaugh() {
        if (mCommandLibrary != null) {
            var text = "<anim cat='laughing' nonBlocking='true' endNeutral='true'/><ssa cat='laughing'/>"
            mCommandLibrary?.say(text, this)
        }
    }

    fun esmlQuestion() {
        if (mCommandLibrary != null) {
            var text = "<anim cat='confused' nonBlocking='true' endNeutral='true'/><ssa cat='oops'/>"
            mCommandLibrary?.say(text, this)
        }
    }

    fun esmlSad() {
        if (mCommandLibrary != null) {
            var text = "<anim cat='sad' nonBlocking='true' endNeutral='true'/><ssa cat='sad'/>"
            mCommandLibrary?.say(text, this)
        }
    }

    fun esmlPassive(prob: Int) {
        if (mCommandLibrary != null) {
            var rand = Math.random() * 100
            var text = "<anim cat='laughing' endNeutral='true' layers='body'/>"
            if (rand < 16)
                text = "<anim cat='frustrated' endNeutral='true' layers='body'/>"
            else if (rand < 32)
                text = "<anim cat='affection' endNeutral='true' layers='body'/>"
            else if (rand < 48)
                text = "<anim cat='relieved' endNeutral='true' layers='body'/>"
            else if (rand < 64)
                text = "<anim cat='happy' endNeutral='true' layers='body'/>"
            else if (rand < 70)
                text = "<anim cat='excited' endNeutral='true' layers='body'/>"
            if (Math.random() * 100 < prob) {
                latestCommandID = mCommandLibrary?.say(text, this)
                log("Passive movement: $text", 0)
            }
        }
    }

    fun esmlNod() {
        if (mCommandLibrary != null){
            repeat(2) {
                if (jiboPosition[2] != 1) {
                    onMoveClick(intArrayOf(jiboPosition[0], jiboPosition[1], 1))
                    Thread.sleep((500 * Math.abs(jiboPosition[2] - 1).toLong()))
                }
                onMoveClick(intArrayOf(jiboPosition[0], jiboPosition[1], 0))
                Thread.sleep(400)
                onMoveClick(intArrayOf(jiboPosition[0], jiboPosition[1], 1))
                Thread.sleep(1000)
            }
        }
    }

    fun say(text : String) {
        if (mCommandLibrary != null && System.currentTimeMillis() - lastJiboSpeech > 3000) {
            log(text, 0)
            lastJiboSpeech = System.currentTimeMillis()
            mCommandLibrary?.say(text, this)
        }
    }

    // Interact Button
    fun onInteractClick() {
        if (mCommandLibrary != null) {
            log("onInteractClick was successfully called")
            /*
            var actions = arrayOf("affection", "confused", "embarrassed",
                    "excited", "frustrated", "happy", "headshake", "laughing", "nod", "proud",
                    "relieved", "sad", "scared", "worried")
            var soundeffects = arrayOf("proud", "surprised", "confused", "scared",
                    "embarrased", "affection", "sad", "happy", "disgusted",
                    "yawn", "laughing", "worried", "dontknow", "frustrated",
                    "oops", "question", "thinking", "hello", "goodbye", "no", "confirm")

            esmlLaugh()
            esmlProud()
            esmlQuestion()
            esmlSad()
            esmlPassive()

            for (act in actions) {
                var text = "<anim cat='$act' endNeutral='true' layers='body'/>"
                var displayView = Command.DisplayRequest.TextView("Text", act)
                mCommandLibrary?.display(displayView, this)
                Thread.sleep(1000)
                mCommandLibrary?.display(Command.DisplayRequest.EyeView("Eye"), this)
                mCommandLibrary?.say(text, this)
                Thread.sleep(6000)
            }

            for (ssa in soundeffects) {
                var text = "<ssa cat='$ssa'/>"
                var displayView = Command.DisplayRequest.TextView("Text", ssa)
                mCommandLibrary?.display(displayView, this)
                Thread.sleep(1000)
                mCommandLibrary?.say(text, this)
                Thread.sleep(6000)
            }*/
            /*

            var texts = arrayOf("<pitch halftone=\"3\"> Halftone, 3</pitch> ",
                    "<pitch halftone=\"-3\"> Halftone, negative 3</pitch> ",
                    "<pitch band=\"1\"> Bandwidth, 1 </pitch> ",
                    "<pitch band=\"2\"> Bandwidth, 2 </pitch> ",
                    "<pitch band=\"2.5\"> Bandwidth, 2.5 </pitch> ",
                    "<pitch add=\"-100\"> Additional Frequency, negative 100 </pitch> ",
                    "<pitch add=\"100\"> Additional Frequency, 100 </pitch> ",
                    "<pitch mult=\"0.8\"> Multiplicative Frequency, 0.8 </pitch> ",
                    "<pitch mult=\"1.2\"> Multiplicative Frequency, 1.2 </pitch> ")
            for (text in texts){
                mCommandLibrary?.say(text, this)
                Thread.sleep(3000)
            }*/
            /*
            var text = "<style set=\"confused\"><duration stretch=\"1.2\"> Hey Jibo. <break size='1'/> How are you doing? </duration></style>"
            if (Math.random() * 10 < 5)
                text = "<style set=\"enthusiastic\"><pitch band=\"1.5\"><duration stretch=\"1.2\"> Good morning! I hope you have a wonderful day! </duration></pitch></style>"
            else if (Math.random() * 10 < 5)
                text = "<pitch halftone=\"2\"><duration stretch=\"1.2\"> Hi, my name is Jibo. I am a robot. </duration></pitch>"
            latestCommandID = mCommandLibrary?.say(text, this)
            */
            esmlNod()
        }
    }

    // Listen Button
    fun onListenClick() {
        if (mCommandLibrary != null) {
            log("onListenClick was successfully called")
            if (listenButton.isChecked)
                latestCommandID = mCommandLibrary?.listen(10L, 10L, "en", this)
        }
    }

    // Move Button (3 functions for 3 different input types)
    fun onMoveClick() : String? {
        if (mCommandLibrary != null) {
            log("onMoveClick successfully called")
            var deltaX = 0
            var deltaY = 0
            var deltaZ = 0
            if (positionTextX.text.toString() != "")
                deltaX = Integer.parseInt(positionTextX.text.toString())
            if (positionTextY.text.toString() != "")
                deltaY = Integer.parseInt(positionTextY.text.toString())
            if (positionTextZ.text.toString() != "")
                deltaZ = Integer.parseInt(positionTextZ.text.toString())
            //var target = Command.LookAtRequest.AngleTarget(intArrayOf(deltaX, deltaY))
            var target = Command.LookAtRequest.PositionTarget(intArrayOf(deltaX, deltaY, deltaZ))
            jiboPosition = intArrayOf(deltaX, deltaY, deltaZ)
            return mCommandLibrary?.lookAt(target, this)
        }
        return null
    }

    fun onMoveClick(position: Int) : String? {
        if (mCommandLibrary != null){
            var target = intArrayOf(4, -1, 1)
            if (position == 2)
                target = intArrayOf(2, 2, 1)
            else if (position == 3)
                target = intArrayOf(1, 4, 1)
            else if (position == 4)
                target = intArrayOf(2, -2, 1)
            jiboPosition = target
            return mCommandLibrary?.lookAt(Command.LookAtRequest.PositionTarget(target), this)
        }
        return null
    }

    fun onMoveClick(position: IntArray) : String? {
        if (mCommandLibrary != null){
            jiboPosition = position
            return mCommandLibrary?.lookAt(Command.LookAtRequest.PositionTarget(position), this)
        }
        return null
    }

    fun passiveMovement(prob : Int){
        var randPos = Math.random() * 100
        if (randPos < prob/4)
            latestCommandID = onMoveClick(1)
        else if (randPos < 2 * prob/4)
            latestCommandID = onMoveClick(2)
        else if (randPos < 3 * prob/4)
            latestCommandID = onMoveClick(3)
        else if (randPos < prob)
            latestCommandID = onMoveClick(4)
    }

    override fun onConnected() {}

    override fun onSessionStarted(commandLibrary: CommandLibrary) {
        mCommandLibrary = commandLibrary
        runOnUiThread {
            // Once we're connected and ready for commands,
            // enable the Disconnect and Say buttons
            disconnectButton?.isEnabled = true
            interactButton?.isEnabled = true
            listenButton?.isEnabled = true
            moveButton?.isEnabled = true
            cancelButton?.isEnabled = true

            // Log that we're connected to the app
            Toast.makeText(this@MainActivity, "Connected", Toast.LENGTH_SHORT).show()

            // start running the background activity
            val timer = Timer()
            timer.schedule(BackgroundActivity(), 10000, 10000)
        }
    }

    override fun onConnectionFailed(throwable: Throwable) {
        runOnUiThread {
            // If connection fails, re-enable the Connect button so we can try again
            connectButton?.isEnabled = true

            // Log the error to the app
            Toast.makeText(this@MainActivity, "Connection failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDisconnected(i: Int) {
        runOnUiThread {
            // Re-enable Connnect & Say when we're disconnected
            connectButton?.isEnabled = true
            interactButton?.isEnabled = false

            // Log that we've disconnected from the app
            Toast.makeText(this@MainActivity, "Disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    // onCommandResponseListener overrides

    override fun onSuccess(s: String) {
        runOnUiThread { }
    }

    override fun onError(s: String, s1: String) {
        runOnUiThread {
            // Log the error to the app
            Toast.makeText(this@MainActivity, "error : $s $s1", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onEventError(s: String, errorData: EventMessage.ErrorEvent.ErrorData) {

        runOnUiThread {
            // Log the error to the app
            Toast.makeText(this@MainActivity, "error : " + s + " " + errorData.errorString, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSocketError() {
        runOnUiThread {
            // Log the error to the app
            Toast.makeText(this@MainActivity, "socket error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onEvent(s: String, baseEvent: EventMessage.BaseEvent) {
        //log("String: $s, BaseEvent: $baseEvent")
        log("Event detected: " + baseEvent.event.name)
        if (baseEvent.toString().contains("StopEvent") && listenButton.isChecked) {
            onListenClick()
        }
    }

    override fun onPhoto(s: String, takePhotoEvent: EventMessage.TakePhotoEvent, inputStream: InputStream) {}

    override fun onVideo(s: String, videoReadyEvent: EventMessage.VideoReadyEvent, inputStream: InputStream) {}

    override fun onListen(transactID: String, speech: String) {
        var proudList = listOf("happy", "cool", "fun", "great", "amazing", "wonderful",
                "fantastic", "yes", "nice", "congrats", "congratulations", "yay", "best", "thanks",
                "hurray", "woohoo", "woo hoo", "excited", "exciting", "not bad", "wasn't bad")
        var laughList = listOf("funny", "hilarious", "haha", "ha ha", "laugh")
        var sadList = listOf("oh no", "yikes", "terrible", "awful", "horrible", " sad ", " bad ",
                "embarrassing", "embarrassed", "not good", "worst", "worse", "sigh", "frustrated",
                "frustrating", "stupid", "dumb", "sucks", "shit")
        var questionList = listOf("confused", "don't know", "do not know", "dunno", "jibo", "tebow",
                "question", "robot", "not sure", "wonder if", "?")
        var text = "$speech"
        var responses = arrayOf("")
        var tempSleep = 0
        if (nonverbalBCSwitch.isChecked) {
            if (checkFor(text, proudList)) {
                onCancelClick()
                esmlProud()
                log("proud behavior activated", 0)
            } else if (checkFor(text, laughList)) {
                onCancelClick()
                esmlLaugh()
                log("laugh behavior activated", 0)
            } else if (checkFor(text, questionList)) {
                onCancelClick()
                esmlQuestion()
                log("question behavior activated", 0)
            } else if (checkFor(text, sadList)) {
                onCancelClick()
                esmlSad()
                log("sad behavior activated", 0)
            } else if (Math.random() * 100 < nonverbalBCProbBar.progress) {
                esmlNod()
                tempSleep = 3000
                log("nod behavior activated", 0)
            }
            Thread.sleep(tempSleep.toLong())
        }
        if (verbalBCSwitch.isChecked && transactID == "Manual_Long"){
            log("long response activated", -1)
            var restOfSentence = ""
            responses = arrayOf("that's worth considering", "good idea", "I see", "that makes sense",
                    "that's reasonable", "uh huh", "<pitch add=\"25\"><style set=\"enthusiastic\"><duration stretch=\"1.5\"><phoneme ph='h m'>Hmm?</phoneme></duration></style></pitch>",
                    "<pitch add=\"25\"><style set=\"enthusiastic\"><duration stretch=\"1.5\"><phoneme ph='h m'>Hmm?</phoneme></duration></style></pitch> maybe", "interesting", "okay", "yeah",
                    "that's interesting", "what do we think about that?", "thoughts?",
                    "let's think about that")
            var items = listOf(" Coffee pot "," Screwdriver "," sharpies ", " sharpie ", " rubber band ",
                    " rubber bands "," CD "," Camera "," Watch "," teddy bear "," underwear ","newspaper "," whiskey "," chocolate "," Whistle "," soda "," Shoelaces ",
                    " key "," Light bulb "," tape ", " Umbrella "," honey "," floss "," Garbage bag "," Condom "," Spoon "," Chapstick "," lip balm ", " coke ")
            if (checkFor(text, listOf("i think that")))
                restOfSentence = text.substring(text.toLowerCase().indexOf("i think") + 12)
            else if (checkFor(text, listOf("i think")))
                restOfSentence = text.substring(text.toLowerCase().indexOf("i think") + 7)
            else if (checkFor(text, listOf("i feel like")))
                restOfSentence = text.substring(text.toLowerCase().indexOf("i feel like") + 11)
            else if (checkFor(text, listOf("i'm pretty sure")))
                restOfSentence = text.substring(text.toLowerCase().indexOf("i'm pretty sure") + 15)
            else if (checkFor(text, listOf("i wonder if")))
                restOfSentence = text.substring(text.toLowerCase().indexOf("i wonder if") + 11)
            else if (checkFor(text, listOf("let's")))
                restOfSentence = text.substring(text.toLowerCase().indexOf("let's") + 5)
            else if (checkFor(text, listOf("we can")))
                restOfSentence = text.substring(text.toLowerCase().indexOf("we can") + 6)
            else if (checkFor(text, listOf("i don't think"))) {
                restOfSentence = "you don't think" + text.substring(text.toLowerCase().indexOf("i don't think") + 13)
            }
            var substitutions = listOf(" ha ", " ", "hahaha", " ", "haha", " ",
                    "i've", "you've", " i have", " you have", "i'd", "you'd", "i'll", "you'll",
                    "i'm", "you're", " i am", " you are", " i ", " you ", " um ", " ", " uh ", " ", " feel like ", " feel as if "," like ", " ", " uhh " , " ")
            var subIndex = 0
            restOfSentence = restOfSentence.toLowerCase()
            while (subIndex < substitutions.size){
                var replacedStr = substitutions[subIndex]
                var replaceStr = substitutions[subIndex + 1]
                restOfSentence = restOfSentence.replace(replacedStr, replaceStr)
                subIndex += 2
            }
            var matches = getMatching(text, items)
            if (matches.size>0) {
                restOfSentence=""
                for (word in matches)
                    restOfSentence+= word + ", "
            }
            restOfSentence = restOfSentence + " <phoneme ph='LPAU'>Pause</phoneme></duration></style></pitch> " + getRandom(responses, 90)
            say(restOfSentence)
        } else if (verbalBCSwitch.isChecked) {
            tempSleep = 3000
            var canCancel = true
            if (checkFor(text, listOf("Jibo", "Tebow"))) {
                text = "Hi! Did someone say Jibo? How can I help you?"
            } else if (text.toLowerCase().contains(" right?")) {
                responses = arrayOf("right!", "yup", "exactly", "agreed")
                text = "<style set=\"enthusiastic\">" + getRandom(responses) + "</style>"
            } else if (checkFor(text, listOf("hello", " hi "))) {
                responses = arrayOf("Hi! How are you?", "hello", "How do you do?", "hey!",
                        "hey there", "hi", "nice to meet you!")
                text = "<style set=\"enthusiastic\">" + getRandom(responses) + "</style>"
            } else if (text.toLowerCase().contains("you hear me")) {
                text = "<style set=\"enthusiastic\">Yeah, I'm listening!</style>"
            } else if (text.toLowerCase().contains("let's")){
                responses = arrayOf("Let's think about that", "Do we all agree?",
                        "What does everyone think about that?",
                        "Does anyone have thoughts on that?", "<phoneme ph='h m'>Hmm</phoneme>")
                text = "<style set=\"enthusiastic\">" + getRandom(responses, verbalBCProbBar.progress) + "</style>"
            } else if (checkFor(text, listOf("i think", "what if", "what about", "how about",
                            "make sense", "i feel like", "pretty sure"))){
                responses = arrayOf("That makes sense", "That's worth considering", "Good idea!")
                text = "<style set=\"enthusiastic\">" + getRandom(responses, verbalBCProbBar.progress) + "</style>"
            } else if (checkFor(text, listOf("?", "wonder if", "not sure"))) {
                responses = arrayOf("That's something to think about", "<phoneme ph='h m'>Hmm</phoneme>")
                text = "<style set=\"enthusiastic\">" + getRandom(responses, verbalBCProbBar.progress) + "</style>"
            } else {
                tempSleep = 0
                canCancel = false
                var rand = Math.random() * 100
                if (rand < verbalBCProbBar.progress/9)
                    text = "<pitch add=\"25\"><style set=\"sheepish\"><duration stretch=\"1.2\">Yeah</duration></style></pitch>"
                else if (rand < 2 * verbalBCProbBar.progress/9)
                    text = "<pitch add=\"25\"><style set=\"sheepish\"><duration stretch=\"1.2\">Yes!</duration></style></pitch>"
                else if (rand < 3 * verbalBCProbBar.progress/9)
                    text = "<pitch add=\"25\"><style set=\"enthusiastic\"><duration stretch=\"0.75\">Uh huh!</duration></style></pitch>"
                else if (rand < 4 * verbalBCProbBar.progress/9)
                    text = "<pitch add=\"25\"><style set=\"enthusiastic\"><duration stretch=\"1.5\"><phoneme ph='h m'>Hmm?</phoneme></duration></style></pitch>"
                else if (rand < 5 * verbalBCProbBar.progress/9)
                    text = "<style set=\"enthusiastic\"><duration stretch=\"1.3\">I see</duration></style>"
                else if (rand < 6 * verbalBCProbBar.progress/9)
                    text = "<pitch add=\"25\"><style set=\"sheepish\"><duration stretch=\"1.7\">Wow</duration></style></pitch>"
                else if (rand < 7 * verbalBCProbBar.progress/9)
                    text = "<pitch add=\"25\"><style set=\"sheepish\"><duration stretch=\"1.2\">Okay</duration></style></pitch>"
                else if (rand < 8 * verbalBCProbBar.progress/9)
                    text = "<pitch add=\"25\"><style set=\"confused\"><duration stretch=\"1.3\">Interesting</duration></style></pitch>"
                else if (rand < 9 * verbalBCProbBar.progress/9)
                    text = "<pitch add=\"25\"><style set=\"confused\"><duration stretch=\"1.3\">Right</duration></style></pitch>"
                else
                    text = ""
            }
            if (canCancel)
                onCancelClick()
            say(text)
            Thread.sleep(tempSleep.toLong())
        }
    }

    override fun onParseError() {}

}
