
package com.kkek.assistant.telecom

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle

class CallConnectionService : ConnectionService() {
    override fun onCreateOutgoingConnection(connectionManager: PhoneAccountHandle?, request: ConnectionRequest?): Connection {
        return super.onCreateOutgoingConnection(connectionManager, request)
    }
}
