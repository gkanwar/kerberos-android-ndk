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

public class KerberosAgentService extends Service implements KinitPrompter, AppendTextInterface{
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
                    }
                    catch (NumberFormatException e) {
                        host.close();
                        continue;
                    }
                    Log.v("KERBEROS", new String(full_msg));
                    String[] tokens = new String(full_msg).split("\n");
                    if (tokens.length < 2) {
                        host.close();
                        continue;
                    }
                    String cmd = tokens[0];
                    Log.v("KERBEROS", "Cmd: " + cmd);
                    if (cmd.equals("ticket")) {
                        String service = tokens[1];
                        if (service.startsWith("krbtgt")) {
                            Log.w("KERBEROS", "Attempt to get krbtgt");
                            host.close();
                            continue;
                        }
                        // TODO: User confirmation
                        Log.i("KERBEROS", "Got service ticket request: " + service);
                        int ret = KerberosAppActivity.kvno(service, KerberosAgentService.this);
                        if (ret != 0) {
                            // Fail
                            Log.e("KERBEROS", "Agent ticket request failed: " + service + ", err code: " + ret);
                            host.getOutputStream().write("FAIL".getBytes());
                        }
                        else {
                            // Success
                            Log.i("KERBEROS", "Agent ticket request success: " + service);
                            // TODO: Send the ticket itself
                            host.getOutputStream().write("OK".getBytes());
                        }
                    }
                    else if (cmd.equals("kinit")) {
                        String principal = tokens[1];
                        Log.i("KERBEROS", "Got kinit request: " + principal);
                        int ret = KerberosAppActivity.kinit(principal, KerberosAgentService.this, KerberosAgentService.this);
                        if (ret != 0) {
                            // Fail
                            Log.e("KERBEROS", "Agent kinit failed: " + principal + ", err code: " + ret);
                            host.getOutputStream().write("FAIL".getBytes());
                        }
                        else {
                            // Success
                            Log.i("KERBEROS", "Agent kinit success: " + principal);
                            host.getOutputStream().write("OK".getBytes());
                        }
                    }
                    host.close();
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

    @Override
    public void appendText(String input) {
        Log.i("KERBEROS", input);
    }

    @Override
    public String[] kinitPrompter(String name, String banner, Prompt[] prompts) {
        // TODO: Implement this cleanly
        String[] out = new String[prompts.length];
        for (int i = 0; i < prompts.length; ++i) {
            out[i] = "";
        }
        return out;
    }
}
