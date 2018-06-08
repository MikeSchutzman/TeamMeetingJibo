package com.example.android.teammeetingjibo

import android.content.Context
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Switch
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
        Log.d("TeamMeeting", msg)
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
            //mCommandLibrary?.listen(15L, 3600L, "en", this)
            log("onInteractClick was successfully called")
            var text = "<style set=\"sheepish\"> Hi, this is sheepish styled speech.</style>" +
                       "<style set=\"enthusiastic\"> And this is enthusiastic styled speech.</style>" +
                       "<style set=\"confused\"> And this is me being confused.</style>" +
                       "<style set=\"confident\"> And here is me being confident.</style>"
            mCommandLibrary?.say(text, this)
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
            if (rotateHorizontal.isChecked || rotateVertical.isChecked) {
                var deltaX = 0
                var deltaY = 0
                if (rotateHorizontal.isChecked)
                    deltaX = 3
                if (rotateVertical.isChecked)
                    deltaY = 3
                var target = Command.LookAtRequest.AngleTarget(intArrayOf(deltaX, deltaY))
                mCommandLibrary?.lookAt(target, this)
            }
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
        var text = "Here's what I heard: $speech"
        if (text == ""){
            text = "Sorry, did you say something?"
        } else if (text.contains("jibo")){
            text = "Hi! Did someone say Jibo? How can I help you?"
        }
        mCommandLibrary?.say(text, this)
    }

    override fun onParseError() {}

}
