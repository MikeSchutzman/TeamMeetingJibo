package com.example.android.teammeetingjibo

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.jibo.apptoolkit.protocol.CommandLibrary
import com.jibo.apptoolkit.protocol.OnConnectionListener
import com.jibo.apptoolkit.protocol.model.EventMessage
import com.jibo.apptoolkit.android.JiboRemoteControl
import com.jibo.apptoolkit.android.model.api.Robot
import java.io.InputStream
import android.widget.Toast
import com.jibo.apptoolkit.protocol.model.Command
import kotlinx.android.synthetic.main.activity_main.*
import java.util.ArrayList

class MainActivity : AppCompatActivity(), OnConnectionListener, CommandLibrary.OnCommandResponseListener {

    // Variable for using the command library
    private var mCommandLibrary: CommandLibrary? = null

    // List of robots associated with a user's account
    private var mRobots: ArrayList<Robot>? = null

    private var latestCommandID: String? = null

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
    // Our connectivity functions

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

        // Disable the connect button while we're connecting
        // to prevent double-clicking
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

    // Cancels the listening action
    fun onCancelClick() {
        if (mCommandLibrary != null && latestCommandID != null) {
            latestCommandID = mCommandLibrary?.cancel(latestCommandID, this)
        }
    }

    fun esmlProud() {
        if (mCommandLibrary != null) {
            var text = "<anim cat='happy' nonBlocking='true' endNeutral='true'/><ssa cat='proud'/>"
            mCommandLibrary?.say(text, this)
            Thread.sleep(5000)
        }
    }

    fun esmlLaugh() {
        if (mCommandLibrary != null) {
            var text = "<anim cat='laughing' nonBlocking='true' endNeutral='true'/><ssa cat='laughing'/>"
            mCommandLibrary?.say(text, this)
            Thread.sleep(5000)
        }
    }

    fun esmlQuestion() {
        if (mCommandLibrary != null) {
            var text = "<anim cat='confused' nonBlocking='true' endNeutral='true'/><ssa cat='oops'/>"
            mCommandLibrary?.say(text, this)
            Thread.sleep(5000)
        }
    }

    fun esmlSad() {
        if (mCommandLibrary != null) {
            var text = "<anim cat='sad' nonBlocking='true' endNeutral='true'/><ssa cat='sad'/>"
            mCommandLibrary?.say(text, this)
            Thread.sleep(5000)
        }
    }

    fun esmlPassive() {
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
            if (Math.random() * 100 < 25) {
                mCommandLibrary?.say(text, this)
                log(text)
            }
            Thread.sleep(4000)
        }
    }


    // Interact Button
    fun onInteractClick() {
        if (mCommandLibrary != null) {
            log("onInteractClick was successfully called")
            var actions = arrayOf("affection", "confused", "embarrassed",
                    "excited", "frustrated", "happy", "headshake", "laughing", "nod", "proud",
                    "relieved", "sad", "scared", "worried")
            var soundeffects = arrayOf("proud", "surprised", "confused", "scared",
                    "embarrased", "affection", "sad", "happy", "disgusted",
                    "yawn", "laughing", "worried", "dontknow", "frustrated",
                    "oops", "question", "thinking", "hello", "goodbye", "no", "confirm")
            /*
            esmlLaugh()
            esmlProud()
            esmlQuestion()
            esmlSad()
            esmlPassive()*/
            /*
            for (act in actions) {
                var text = "<anim cat='$act' endNeutral='true' layers='body'/>"
                var displayView = Command.DisplayRequest.TextView("Text", act)
                mCommandLibrary?.display(displayView, this)
                Thread.sleep(1000)
                mCommandLibrary?.display(Command.DisplayRequest.EyeView("Eye"), this)
                mCommandLibrary?.say(text, this)
                Thread.sleep(6000)
            }*/
            /*
            for (ssa in soundeffects) {
                var text = "<ssa cat='$ssa'/>"
                var displayView = Command.DisplayRequest.TextView("Text", ssa)
                mCommandLibrary?.display(displayView, this)
                Thread.sleep(1000)
                mCommandLibrary?.say(text, this)
                Thread.sleep(6000)
            }*/
            /*
            var speakingStyle = arrayOf("neutral", "enthusiastic",
                    "sheepish", "confused", "confident")
            var backChannels = arrayOf("yeah", "yes", "uh huh",
                    "right", "okay", "wow!")
            for (style in speakingStyle) {
                mCommandLibrary?.say("<style set=\"$style\"> The current speaking style is: $style </style>", this)
                Thread.sleep(3000)
                for (bc in backChannels) {
                    var text = "<style set=\"$style\"> $bc </style>"
                    mCommandLibrary?.say(text, this)
                    Thread.sleep(1500)
                }
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

            mCommandLibrary?.listen(10L, 15L, "en", this)
            Thread.sleep(2000)
            var text = "<style set=\"confused\"><duration stretch=\"1.2\"> Hey Jibo. <break size='1'/> How are you doing? </duration></style>"
            if (Math.random() * 10 < 5)
                text = "<style set=\"enthusiastic\"><pitch band=\"1.5\"><duration stretch=\"1.2\"> Good morning! I hope you have a wonderful day! </duration></pitch></style>"
            else if (Math.random() * 10 < 5)
                text = "<pitch halftone=\"2\"><duration stretch=\"1.2\"> Hi, my name is Jibo. I am a robot. </duration></pitch>"
            mCommandLibrary?.say(text, this)
        }
    }

    // Listen Button
    fun onListenClick() {
        if (mCommandLibrary != null) {
            log("onListenClick was successfully called")
            latestCommandID = mCommandLibrary?.listen(10L, 10L, "en", this)
            if (passiveButton.isChecked)
                esmlPassive()
            if (passiveMoveButton.isChecked)
                passiveMovement()
        }
    }

    // Move Button
    fun onMoveClick() {
        if (mCommandLibrary != null) {
            //var target = Command.LookAtRequest.PositionTarget(intArrayOf(10, 1, 1))
            //var target = Command.LookAtRequest.AngleTarget(intArrayOf(3, 1))
            //mCommandLibrary?.lookAt(target, this)
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
            mCommandLibrary?.lookAt(target, this)

        }
    }

    fun onMoveClick(position: Int) {
        // 1, 1, 1
        // 2, -3, 1
        // -2, 1, 1
        // -2, -3, 1
        if (mCommandLibrary != null){
            var target = Command.LookAtRequest.PositionTarget(intArrayOf(1, 1, 1))
            if (position == 2)
                target = Command.LookAtRequest.PositionTarget(intArrayOf(2, -3, 1))
            else if (position == 3)
                target = Command.LookAtRequest.PositionTarget(intArrayOf(-2, -3, 1))
            else if (position == 4)
                target = Command.LookAtRequest.PositionTarget(intArrayOf(-2, 1, 1))
            mCommandLibrary?.lookAt(target, this)
        }
    }

    fun passiveMovement(){
        var randPos = Math.random() * 100
        if (randPos < 5)
            onMoveClick(1)
        else if (randPos < 10)
            onMoveClick(2)
        else if (randPos < 15)
            onMoveClick(3)
        else if (randPos < 20)
            onMoveClick(4)

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
        log(baseEvent.event.name)
        if (baseEvent.toString().contains("StopEvent")) {
            onListenClick()
        }
    }

    override fun onPhoto(s: String, takePhotoEvent: EventMessage.TakePhotoEvent, inputStream: InputStream) {}

    override fun onVideo(s: String, videoReadyEvent: EventMessage.VideoReadyEvent, inputStream: InputStream) {}

    override fun onListen(transactID: String, speech: String) {
        var proudList = listOf("happy", "cool", "fun", "great", "good", "amazing", "wonderful",
                "fantastic", "yes", "nice", "congrats", "congratulations", "yay", "best", "thanks",
                "hurray", "woohoo", "woo hoo")
        var laughList = listOf("funny", "hilarious", "haha", "ha ha", "laugh")
        var sadList = listOf("oh no", "yikes", "terrible", "awful", "horrible", "sad", "bad",
                "embarrassing", "embarrassed", "not good", "worst", "worse", "sigh")
        var questionList = listOf("confused", "don't know", "dunno", "jibo", "question", "robot")
        log("Heard: $speech")
        var text = speech
        if (nonverbalBCSwitch.isChecked) {
            if (checkFor(text, proudList)) {
                esmlProud()
                log("proud behavior activated")
            } else if (checkFor(text, laughList)) {
                esmlLaugh()
                log("laugh behavior activated")
            } else if (checkFor(text, questionList)) {
                esmlQuestion()
                log("question behavior activated")
            } else if (checkFor(text, sadList)) {
                esmlSad()
                log("sad behavior activated")
            } else if (Math.random() * 10 < 5) {
                esmlPassive()
                log("passive behavior activated")
            }
        }
        if (verbalBCSwitch.isChecked) {
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
            } else if (checkFor(text, listOf("i think", "what if", "what about", "how about"))){
                text = "<style set=\"enthusiastic\">That's worth considering</style>"
            } else if (text.toLowerCase().contains("make sense")) {
                text = "<style set=\"enthusiastic\">That makes sense</style>"
            } else {
                var rand = Math.random() * 100
                if (rand < 20)
                    text = "<pitch add=\"25\"><style set=\"sheepish\"><duration stretch=\"1.2\">Yeah</duration></style></pitch>"
                if (rand < 25)
                    text = "<pitch add=\"25\"><style set=\"sheepish\"><duration stretch=\"1.2\">Yes!</duration></style></pitch>"
                else if (rand < 40)
                    text = "<pitch add=\"25\"><style set=\"enthusiastic\"><duration stretch=\"0.5\">Uh huh!</duration></style></pitch>"
                else if (rand < 50)
                    text = "<pitch add=\"10\"><style set=\"enthusiastic\"><duration stretch=\"1.5\"><phoneme ph='hum mm mm m'>Hmm?</phoneme></duration></style></pitch>"
                else if (rand < 60)
                    text = "<style set=\"enthusiastic\"><duration stretch=\"1.3\">I see</duration></style>"
                else if (rand < 70)
                    text = "<pitch add=\"25\"><style set=\"sheepish\"><duration stretch=\"1.7\">Wow</duration></style></pitch>"
                else if (rand < 80)
                    text = "<pitch add=\"25\"><style set=\"sheepsih\"><duration stretch=\"1.2\">Okay</duration></style></pitch>"
                else if (rand < 85)
                    text = "<pitch add=\"25\"><style set=\"confused\"><duration stretch=\"1.3\">Interesting</duration></style></pitch>"
                else if (rand < 95)
                    text = ""
            }
            log("Jibo's Reply: $text")
            mCommandLibrary?.say(text, this)
        }
        if (specialBCSwitch.isChecked) {
            if (Math.random() * 100 < 2) {
                text = "<style set=\"enthusiastic\">Time for a short break!</style>" +
                        "<anim cat='dance' filter='&music' endNeutral='true'/>"
                mCommandLibrary?.say(text, this)
                Thread.sleep(2000)
            }
        }
        Thread.sleep(4000)
        onListenClick()
    }

    override fun onParseError() {}

}
