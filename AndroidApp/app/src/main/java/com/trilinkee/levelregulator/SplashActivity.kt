package com.trilinkee.levelregulator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Message
import android.os.Handler.Callback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*
import kotlin.concurrent.schedule


class SplashActivity : AppCompatActivity() {
    private enum class StateMachine {
        NONE, REQUEST_ACCESS_COARSE_LOCATION, ACCESS_COARSE_LOCATION, REQUEST_BLUETOOTH_ADMIN, BLUETOOTH_ADMIN, REQUEST_BLUETOOTH, BLUETOOTH, ALL_GRANTED, DONE
    }
    private var mState: StateMachine = StateMachine.NONE
    private enum class StateMachineInput {
        NONE, PERMISSION_GRANTED, PERMISSION_DENIED
    }

    val REQEST_PERMISSION = 1
    var mTimer: TimerTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        mTimer = Timer("Timer", false).schedule(100, 100) {
            stateMachine(StateMachineInput.NONE)
        }
    }

    private fun stateMachine(stateInput: StateMachineInput) {
        when(mState) {
            StateMachine.NONE -> {
                when(stateInput) {
                    StateMachineInput.NONE -> {
                        mState = StateMachine.REQUEST_ACCESS_COARSE_LOCATION
                    }
                }
            }
            StateMachine.REQUEST_ACCESS_COARSE_LOCATION -> {
                when(stateInput) {
                    StateMachineInput.NONE -> {
                        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQEST_PERMISSION)
                            mState = StateMachine.ACCESS_COARSE_LOCATION
                        }
                        else {
                            mState = StateMachine.REQUEST_BLUETOOTH_ADMIN
                        }
                    }
                }
            }
            StateMachine.ACCESS_COARSE_LOCATION -> {
                when(stateInput) {
                    StateMachineInput.PERMISSION_GRANTED -> {
                        mState = StateMachine.REQUEST_BLUETOOTH_ADMIN
                    }
                    StateMachineInput.PERMISSION_DENIED -> {
                        mState = StateMachine.REQUEST_ACCESS_COARSE_LOCATION
                    }
                }
            }
            StateMachine.REQUEST_BLUETOOTH_ADMIN -> {
                when(stateInput) {
                    StateMachineInput.NONE -> {
                        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_ADMIN), REQEST_PERMISSION)
                            mState = StateMachine.BLUETOOTH_ADMIN
                        }
                        else {
                            mState = StateMachine.REQUEST_BLUETOOTH
                        }
                    }
                }
            }
            StateMachine.BLUETOOTH_ADMIN -> {
                when(stateInput) {
                    StateMachineInput.PERMISSION_GRANTED -> {
                        mState = StateMachine.REQUEST_BLUETOOTH
                    }
                    StateMachineInput.PERMISSION_DENIED -> {
                        mState = StateMachine.REQUEST_BLUETOOTH_ADMIN
                    }
                }
            }
            StateMachine.REQUEST_BLUETOOTH -> {
                when(stateInput) {
                    StateMachineInput.NONE -> {
                        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH), REQEST_PERMISSION)
                            mState = StateMachine.BLUETOOTH
                        }
                        else {
                            mState = StateMachine.ALL_GRANTED
                        }
                    }
                }
            }
            StateMachine.BLUETOOTH -> {
                when(stateInput) {
                    StateMachineInput.PERMISSION_GRANTED -> {
                        mState = StateMachine.ALL_GRANTED
                    }
                    StateMachineInput.PERMISSION_DENIED -> {
                        mState = StateMachine.REQUEST_BLUETOOTH
                    }
                }
            }
            StateMachine.ALL_GRANTED -> {
                when(stateInput) {
                    StateMachineInput.NONE -> {
                        val intent = Intent(this@SplashActivity, MainActivity::class.java)
                        this@SplashActivity.startActivity(intent)
                        this@SplashActivity.finish()
                        mTimer!!.cancel()
                        mState = StateMachine.DONE
                    }
                }
            }
            StateMachine.DONE -> {
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == REQEST_PERMISSION)
            stateMachine(StateMachineInput.PERMISSION_GRANTED)
        else
            stateMachine(StateMachineInput.PERMISSION_DENIED)
    }
}
