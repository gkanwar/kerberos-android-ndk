package edu.mit.kerberos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

public class KerberosAgentService extends Service {
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    
    @Override
    public IBinder onBind(Intent i) {
        // No binding allowed
        return null;
    }
    
    private char[] getFullMsg(BufferedReader input) throws IOException, NumberFormatException {
        String length_str = input.readLine();
        int offset = 0;
        int ret;
        int length = Integer.parseInt(length_str);
        if (length > 4096) {
            throw new IOException();
        }
        char[] buffer = new char[length];
        Log.i("KERBEROS", "Reading packet of length " + new Integer(length).toString());
        while (length > 0) {
            ret = input.read(buffer, offset, length);
            if (ret < 0) {
                throw new IndexOutOfBoundsException();
            }
            
            length -= ret;
            offset += ret;
        }
        
        return buffer;
    }
    
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }
        
        @Override
        public void handleMessage(Message msg) {
            // Main loop, triggered from the main thread once we are started
            // Sit here and listen for socket connections.
            
            ServerSocket sock;
            try {
                sock = new ServerSocket(8001);
                Log.d("KRB Service", "Started socket on port 8001, listening...");
                while (true) {
                    // Accept socket connections
                    Socket host = sock.accept();
                    BufferedReader input = new BufferedReader(new InputStreamReader(host.getInputStream()));
                    char[] full_msg; 
                    try {
                        full_msg = getFullMsg(input);
                        Log.i("KERBEROS", "Got packet: " + new String(full_msg));
                    }
                    catch (NumberFormatException e) {
                        continue;
                    }
                    Log.v("KERBEROS", new String(full_msg));
                    String[] tokens = new String(full_msg).split("\n");
                    if (tokens.length < 2) {
                        continue;
                    }
                    String cmd = tokens[0];
                    if (cmd == "ticket") {
                        String service = tokens[1];
                        Log.i("KERBEROS", "Got service ticket request: " + service);
                    }
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            
            // Stop
            stopSelf(msg.arg1);
        }
    }
    
    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }
    
    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        // For each start request send a message to start a job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        mServiceHandler.sendMessage(msg);
        
        // If we get killed after returning from here, restart
        return START_STICKY;
    }
}
