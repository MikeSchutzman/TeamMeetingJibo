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


class MainActivity : AppCompatActivity(), OnConnectionListener, CommandLibrary.OnCommandResponseListener {

    // Variable for using the command library
    private var mCommandLibrary: CommandLibrary? = null
    // List of robots associated with a user's account
    private var mRobots: ArrayList<Robot>? = null
    // Used to be able to cancel certain recent behviors
    private var latestCommandID: String? = null
    // Time and ID of last speech
    private var lastSpeechTime: Long = 0
    private var lastSpeechPID: Int = 0
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
    }

    /* USEFUL HELPER FUNCTIONS */
    // function for logging information
    private fun log(msg: String) {
        Log.d("TMJ", msg)
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

    // function returns the sum of the array of doubles
    private fun getSum(array: Array<Double>) : Double {
        var sum = 0.0
        for (i in array.indices)
            sum += array[i]
        return sum
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
                    if ((radioSpeaker.isChecked || radioInactive.isChecked) &&
                            ( obj!!["transcript"] == "!" )){
                        //  obj!!["speech_duration"].toString().toDouble() > 2)
                        onMoveClick(pid)
                        // onListen("Manual", "!")
                    }

                    // send the speech to be processed for backchannelling
                    var speechConf = String.format("%.2f", obj!!["confidence"].toString().toDouble())
                    log("Confidence $speechConf: " + obj!!["transcript"].toString())
                    if (obj!!["confidence"].toString().toDouble() > 0.8)
                        onListen("Manual", obj!!["transcript"].toString())
                    else if (obj!!["transcript"] != "!")
                        onListen("Manual", "")

                    // if one person has been dominating the speech and is the last person who spoke
                    // then glance away with a certain probability to an inactive PID
                    if (lastSpeechPID == pid && getSum(speechTimes) > 30 &&
                            speechTimes[pid - 1]/getSum(speechTimes) > 0.75){
                        var inactivePID = getInversePropIndex(speechTimes) + 1
                        if (inactivePID != lastSpeechPID && Math.random() * 100 < 25) {
                            log("Glancing away at $inactivePID")
                            var returnPos = jiboPosition
                            onMoveClick(inactivePID)
                            Thread.sleep(3000)
                            onMoveClick(returnPos)
                        }
                    }
                    lastSpeechPID = pid
                    lastSpeechTime = System.currentTimeMillis()
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
                var silentPeriod = (System.currentTimeMillis() - lastSpeechTime > 15000)
                if (silentPeriod){
                    log("Silent period detected from last speech time $lastSpeechTime, " +
                            "current time is " + System.currentTimeMillis())
                    numSilencePeriods += 1
                    // passive behaviors are more active during a silent period
                    if (passiveButton.isChecked)
                        esmlPassive(50)
                    if (passiveMoveButton.isChecked)
                        passiveMovement(50)

                    // if it has been a silent period of 15 seconds, look at least active PID
                    if (radioInactive.isChecked && numSilencePeriods > 1) {
                        /*
                        var lookAtPID = arrayListOf(1)
                        for (i in speechTimes.indices) {
                            if (speechTimes[i] < speechTimes[lookAtPID[0] - 1]) {
                                lookAtPID.clear()
                                lookAtPID.add(i + 1)
                            } else if (speechTimes[i] == speechTimes[lookAtPID[0] - 1])
                                lookAtPID.add(i + 1)
                        }
                        var chosenPID = lookAtPID[Math.floor(Math.random() * lookAtPID.size).toInt()]
                        onMoveClick(chosenPID)
                        log("Directing attention towards $chosenPID with speechtime " + speechTimes[chosenPID - 1])
                        */
                        var inactivePID = getInversePropIndex(speechTimes) + 1
                        onMoveClick(inactivePID)
                        log("Directing attention towards: $inactivePID with speechtime " + speechTimes[inactivePID - 1])
                    }

                    if (specialBCSwitch.isChecked) {
                        if (Math.random() * 400 < 2) {
                            var text = "<style set=\"enthusiastic\">Time for a short break!</style><anim cat='dance' filter='&music' endNeutral='true'/>"
                            log("Special BC Activated")
                            say(text)
                            Thread.sleep(4000)
                        }
                    }
                } else {
                    numSilencePeriods = 0
                    if (passiveButton.isChecked)
                        esmlPassive(24)
                    if (passiveMoveButton.isChecked)
                        passiveMovement(16)
                }
                // every timeUntilReset * 10 seconds, reset the speaking times
                if (timeUntilReset > 0)
                    timeUntilReset -= 1
                else {
                    log("Reset speech times from")
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
                log("Passive movement: $text")
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

    fun getLongResponse(speech: String) {   //returns one of Jibo's long responses if applicable
        var text = "$speech"
        var responses = arrayOf("")
        var restOfSentence = ""
        if (text.toLowerCase().indexOf("i think") == 0) {
            restOfSentence = text.toLowerCase().substring(7)
            responses = arrayOf("Yeah", "Good idea", "Yup", "I agree")
        } else if (text.toLowerCase().indexOf("i feel like") == 0) {
            restOfSentence = text.toLowerCase().substring(11)
            responses = arrayOf("Yup", "Oh, I see,", "Exactly,")
        } else if (text.toLowerCase().indexOf("i'm pretty sure") == 0 || text.toLowerCase().indexOf("i am pretty sure") == 0) {
            restOfSentence = text.toLowerCase().substring(15)
            responses = arrayOf("Yeah", "Maybe", "It makes sense that", "uh huh ")
        } else if (text.toLowerCase().indexOf("who") == 0) {
            restOfSentence = text.toLowerCase().substring(3)
            responses = arrayOf("I know who", "<pitch add=\"10\"><style set=\"neutral\"><duration stretch=\"1.5\"><phoneme ph='uh m'>Um</phoneme></duration></style></pitch> who  ", "I'm not sure who", "I wonder who")
        } else if (text.toLowerCase().indexOf("i wonder if") == 0) {
            restOfSentence = text.toLowerCase().substring(11)
            responses = arrayOf("Hmm it'd be interesting if", "It's worth considering if", "I think")
        } else if (text.toLowerCase().indexOf("let's") == 0) {
            restOfSentence = text.toLowerCase().substring(5)
            responses = arrayOf("Yeah let's", "It'll be great if we", "Yes, we should", "Do you all agree that we should", "Why do you want us to")
        } else if (text.toLowerCase().indexOf("i don't think") == 0) {
            restOfSentence = text.toLowerCase().substring(13)
            responses = arrayOf("Maybe", "I'd be impressed if")
        } else if (text.toLowerCase().indexOf("we can") == 0) {
            restOfSentence = text.toLowerCase().substring(6)
            responses = arrayOf("Yes we can", "Yup let's", "I don't think we should", "Today's a perfect day to")
        } else if (text.toLowerCase().contains("right?")) {
            responses = arrayOf("Yup", "Exactly", "Yeah", "Uh huh", "Okay", "Agreed")
            var rndm = (Math.random()*responses.size+3).toInt()
            if (rndm >= responses.size)
            {
                esmlNod()
            }
        }
        else if (text.contains("?")){
            responses = arrayOf("hmm i'm not sure", "I don't know", "<pitch add=\"10\"><style set=\"neutral\"><duration stretch=\"1.5\"><phoneme ph='uh m'>Um</phoneme></duration></style></pitch> interesting question")
        }
        if (text.toLowerCase().contains("start a conversation"))
            text=getConversationStarter()
        else {
            var num = (Math.random() * responses.size).toInt()
            text = responses[num] + restOfSentence
            text=text.toLowerCase().replace(" ha ", "")
            text=text.toLowerCase().replace(" haha ", "")
            text=text.toLowerCase().replace("i've ", " you've ")
            text=text.toLowerCase().replace("i have ", " you have ")
            text=text.toLowerCase().replace("i'd ", " you'd ")
            text=text.toLowerCase().replace("i would ", " you would ")
            text=text.toLowerCase().replace("i'll ", " you'll ")
            text=text.toLowerCase().replace("i will ", " you will ")
            text=text.toLowerCase().replace("i'm ", " you're ")
            text=text.toLowerCase().replace("i am ", " you are ")
            text=text.toLowerCase().replace("i ", " you ")
        }
        say(text)
    }

    fun getConversationStarter(): String
    {
        var array = arrayOf("Do you guys have any pets? I'm thinking about adopting a robot dog.","Do you guys have any plans for the weekend?", "What is the strangest dream you have ever had? Last night I had a nightmare about robots taking over the world!"
                , "Do you guys have a favorite movie? Personally, I like The Matrix.", "Any of you follow sports? I can't wait for the Yale Versus Harvard football game on November 17th"
                , "Just curious, why'd you guys sign up for this experiment?", "I've been listening to some really catchy songs lately. What type of music are you guys into?"
                , "Are you guys originally from around here? I've lived at Yale my whole life.", "Do you guys have any travel plans for the rest of the summer?", "You guys have any siblings? I'm actually one of ten here at Yale.", "What do you guys think the meaning of life is?")
        return (array[(Math.random()*array.size).toInt()])  //random element from the array
    }

    fun say(text : String) {
        if (mCommandLibrary != null)
            mCommandLibrary?.say(text, this)
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
        var sadList = listOf("oh no", "yikes", "terrible", "awful", "horrible", "sad", "bad",
                "embarrassing", "embarrassed", "not good", "worst", "worse", "sigh", "frustrated",
                "frustrating", "stupid", "dumb", "sucks", "shit")
        var questionList = listOf("confused", "don't know", "do not know", "dunno", "jibo", "tebow",
                "question", "robot", "not sure", "wonder if")
        var text = speech
        var tempSleep = 3000
        if (nonverbalBCSwitch.isChecked) {
            if (checkFor(text, proudList)) {
                onCancelClick()
                esmlProud()
                log("proud behavior activated")
            } else if (checkFor(text, laughList)) {
                onCancelClick()
                esmlLaugh()
                log("laugh behavior activated")
            } else if (checkFor(text, questionList)) {
                onCancelClick()
                esmlQuestion()
                log("question behavior activated")
            } else if (checkFor(text, sadList)) {
                onCancelClick()
                esmlSad()
                log("sad behavior activated")
            } else if (Math.random() * 100 < nonverbalBCProbBar.progress) {
                esmlNod()
                log("nod behavior activated")
            } else {
                tempSleep = 0
            }
            Thread.sleep(tempSleep.toLong())
        }
        if (verbalBCSwitch.isChecked) {
            tempSleep = 3000
            var canCancel = true
            if (checkFor(text, listOf("Jibo", "Tebow"))) {
                text = "Hi! Did someone say Jibo? How can I help you?"
            } else if (text.toLowerCase().contains(" right ")) {
                text = "<style set=\"enthusiastic\">Right!</style>"
            } else if (text.toLowerCase().contains("hello")) {
                text = "<style set=\"enthusiastic\">Hello to you too!</style>"
            } else if (text.toLowerCase().contains(" hi ")) {
                text = "<style set=\"enthusiastic\">Hi! How are you?</style>"
            } else if (text.toLowerCase().contains("hear me")) {
                text = "<style set=\"enthusiastic\">Yeah, I'm listening!</style>"
            } else if (checkFor(text, listOf("i think", "what if", "what about", "how about",
                            "make sense", "i feel like", "pretty sure"))){
                text = "<style set=\"enthusiastic\">That makes sense</style>"
                if (Math.random() * 2 < 1)
                    text = "<style set=\"enthusiastic\">That's worth considering</style>"
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
                else if (rand < 99)
                    text = ""
            }
            if (canCancel) {
                onCancelClick()
                log("Jibo's Reply: $text")
            }
            mCommandLibrary?.say(text, this)
            Thread.sleep(tempSleep.toLong())
        }
        if (longResponseSwitch.isChecked) {
            if (longResponseProbBar.progress > (Math.random()*101).toInt())
                getLongResponse(speech)
        }
    }

    override fun onParseError() {}

}
