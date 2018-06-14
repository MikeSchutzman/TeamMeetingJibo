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

class MainActivity : AppCompatActivity(), OnConnectionListener, CommandLibrary.OnCommandResponseListener{

    // Variable for using the command library
    private var mCommandLibrary: CommandLibrary? = null

    // List of robots associated with a user's account
    private var mRobots: ArrayList<Robot>? = null

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

        // Start with only the Log In button enabled
        loginButton.isEnabled = true
        connectButton.isEnabled = false
        disconnectButton.isEnabled = false
        logoutButton.isEnabled = false
        interactButton.isEnabled = false
        listenButton.isEnabled = false
        moveButton.isEnabled = false
    }
        // Our connectivity functions

    // function for logging information
    private fun log(msg: String) {
        Log.d("TMJ", msg)
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
        interactButton?.isEnabled = false
        listenButton?.isEnabled = false
        moveButton?.isEnabled = false

        // Log that we've logged out to the app
        Toast.makeText(this@MainActivity, "Logged Out", Toast.LENGTH_SHORT).show()
    }

    // Interact Button
    fun onInteractClick() {
        if (mCommandLibrary != null) {
            var actions = arrayOf("affection", "confused", "dance", "embarrassed", "emoji",
                    "excited", "frustrated", "happy", "headshake", "laughing", "nod", "proud",
                    "relieved", "sad", "scared", "worried")
            var soundeffects = arrayOf("proud", "surprised", "confused", "scared",
                    "embarrased", "affection", "sad", "happy", "disgusted",
                    "yawn", "laughing", "worried", "dontknow", "frustrated",
                    "oops", "question", "thinking", "hello", "goodbye", "no", "confirm")
            //var text = "Hello, this message should activate the happy action?"
            log("onInteractClick was successfully called")
            /*for (act in actions) {
                var text = "<anim cat='$act' endNeutral='true'/>"
                var displayView = Command.DisplayRequest.TextView("Text", act)
                mCommandLibrary?.display(displayView, this)
                Thread.sleep(1000)
                mCommandLibrary?.display(Command.DisplayRequest.EyeView("Eye"), this)
                mCommandLibrary?.say(text, this)
                Thread.sleep(5000)
            }*/
            /*
            for (ssa in soundeffects) {
                var text = "<ssa cat='$ssa'/>"
                var displayView = Command.DisplayRequest.TextView("Text", ssa)
                mCommandLibrary?.display(displayView, this)
                Thread.sleep(1000)
                mCommandLibrary?.say(text, this)
                Thread.sleep(5000)
            }*/
            /*
            var speakingStyle = arrayOf("neutral", "enthusiastic",
                    "sheepish", "confused", "confident")
            var backChannels = arrayOf("yeah", "yes", "uh huh",
                    "mm hmm",
                    "hmm",
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
            var texts = arrayOf("This is a pitch test. <pitch halftone=\"3\"> Halftone, 3</pitch> ",
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
            }
        }
    }

    // Listen Button
    fun onListenClick() {
        if (mCommandLibrary != null) {
            mCommandLibrary?.listen(10L, 3600L, "en", this)
            log("onListenClick was successfully called")
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
            // 1, 1, 1
            // 2, -3, 1
            // -2, 1, 1
            // -2, -3, 1
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

    // onConnectionListen overrides

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
        log("String: $s, BaseEvent: $baseEvent")
    }

    override fun onPhoto(s: String, takePhotoEvent: EventMessage.TakePhotoEvent, inputStream: InputStream) {}

    override fun onVideo(s: String, videoReadyEvent: EventMessage.VideoReadyEvent, inputStream: InputStream) {}

    override fun onListen(transactID: String, speech: String) {
        log("Heard: $speech")
        var text = "$speech"
        if (text == ""){
            text = "Sorry, did you say something?"
        } else if (text.toLowerCase().contains("jibo")){
            text = "Hi! Did someone say Jibo? How can I help you?"
        } else if (text.toLowerCase().contains("happy")){
            text = "<anim cat='happy'/>"
        } else if (text.toLowerCase().contains("surprised")){
            text = "<anim cat='surprised' nonBlocking='true' endNeutral='true' />"
        } else if (text.toLowerCase().contains("hello")){
            text = "Hello to you too!"
        } else if (text.toLowerCase().contains(" hi ")){
            text = "Hi! How are you?"
        }
        mCommandLibrary?.say(text, this)
        Thread.sleep(2000)
        onListenClick()
    }

    override fun onParseError() {}

}
