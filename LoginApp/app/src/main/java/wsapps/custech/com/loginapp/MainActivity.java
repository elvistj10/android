package wsapps.custech.com.loginapp;

import android.annotation.SuppressLint;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.techdew.stomplibrary.Stomp;
import com.techdew.stomplibrary.StompClient;
import org.java_websocket.WebSocket;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static java.lang.System.*;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    String token, idUaa, idProfile;
    public StompClient mStompClient;
    String ADDRESS = "35.197.149.34:8080"; // GCLOUD ONLINE HOST
    //String ADDRESS = "172.19.90.193:8080"; // LOCALHOST
    String uriGetAcc = "http://" + ADDRESS + "/uaaapp/api/account";
    String uriGetMyProfile = "http://" + ADDRESS + "/profilesapp/api/profiles/userId/mobile/";
    final OkHttpClient client = new OkHttpClient();
    EditText etUsername, etPass, pesan, tujuan;
    TextView tvStat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    login(); // CALL LOGIN FUNCTION
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        Button bconnWs = (Button) findViewById(R.id.bconnect);
        bconnWs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectStomp(); // CALL STOMP WEBSOCKET FUNCTION
            }
        });

        Button bsend = (Button) findViewById(R.id.bkirim);
        bsend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kirimPesan();
            }
        });

        TextView tvLogout = (TextView) findViewById(R.id.textView);
        tvLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logout();
            }
        });

        etUsername = (EditText) findViewById(R.id.username);
        etPass = (EditText) findViewById(R.id.password);
        tvStat = (TextView) findViewById(R.id.tvStatus);
        pesan = (EditText) findViewById(R.id.etPesan);
        tujuan = (EditText) findViewById(R.id.etTujuan);
    }

    @Override
    public void onClick(View v) {/*NOTHING HERE*/}

    private void login() throws IOException { // AWAL FUNGSI LOGIN

        Log.w("LOGIN", "Clicked");
        final MediaType MEDIA_TYPE = MediaType.parse("application/json");
        JSONObject postdata = new JSONObject();

        try {
            postdata.put("username", etUsername.getText());
            postdata.put("password", etPass.getText());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(MEDIA_TYPE, postdata.toString());
        final Request request = new Request.Builder().url("http://"+ ADDRESS + "/auth/login").post(body)
                .addHeader("Content-Type", "application/json").build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                String mMessage = e.getMessage().toString();
                System.out.println(" FAILURE RESPONSE: " + mMessage);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

                String accessToken = response.body().string();

                try {
                    JSONObject resAuth = new JSONObject(accessToken);
                    token = resAuth.getString("access_token"); // GET VALUE BY STRING KEY
                    System.out.println("ACCESS_TOKEN: " + token);
                    getAccount(); // CALL FUNCTION GET ACCOUNT BY USERNAME AND PASSWORD
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

    }// AKHIR FUNGSI LOGIN

    private void getAccount() { // AWAL FUNGSI GET ACCOUNT

        String uaaAccount;
        final Request request = new Request.Builder().header("Authorization", "Bearer " + token).url(uriGetAcc).build();

                try {
                    Response response = client.newCall(request).execute();
                    uaaAccount = response.body().string();
                    System.out.println("UAA_ACCOUNT: " + uaaAccount);

                    try {
                        JSONObject resAccount = new JSONObject(uaaAccount);
                        idUaa = resAccount.getString("id");
                        System.out.println("ID_UAA_ACCOUNT: " + idUaa);
                        getProfile(); // CALL FUNCTION GET PROFILE BY ID UAA ACCOUNT
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

            } // AKHIR FUNGSI GET ACCOUNT

    private void getProfile() { // AWAL FUNGSI GET PROFILE

        String profileAccount;
        final Request request = new Request.Builder().header("Authorization", "Bearer " + token).url(uriGetMyProfile + idUaa).build();

        try {
            Response response = client.newCall(request).execute();
            profileAccount = response.body().string();
            System.out.println("PROFILE: " + profileAccount);

            try {
                JSONObject resProfile = new JSONObject(profileAccount);
                idProfile = resProfile.getString("id");
                System.out.println("ID_PROFILE: " + idProfile);
            } catch (JSONException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    } // AKHIR FUNGSI GET PROFILE

    @SuppressLint("LongLogTag")
    public void connectStomp() { // AWAL FUNGSI KONEKSI WEBSOCKET
        try {
            String urlWs = "ws://" + ADDRESS + "/websocket/tracker/websocket?access_token=" + token;

            mStompClient = Stomp.over(WebSocket.class, urlWs);

            mStompClient.connect();//START WEBSOCKET CONNECTION

            mStompClient.send("/app/saveToRedis/" + idUaa);

            mStompClient.topic("/app/dataChatting").subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread()).subscribe(topicMessage -> {
                Log.d("CHATTING DATA", topicMessage.getPayload());
                tvStat.setText(topicMessage.getPayload());
            });

            mStompClient.topic("/user/queue/pesan").subscribeOn(Schedulers.io()) // SUBSCIBE KE PESAN PRIVATE
                    .observeOn(AndroidSchedulers.mainThread()).subscribe(topicMessage -> {
                toast(topicMessage.getPayload());
            });

            mStompClient.topic("/app/onlineUsers").subscribeOn(Schedulers.io()) // SUBSCRIBE ONLINE USERS
                    .observeOn(AndroidSchedulers.mainThread()).subscribe(topicMessage -> {
                toast(topicMessage.getPayload());
            });

            mStompClient.topic("/app/offlineUsers").subscribeOn(Schedulers.io()) // SUBSCRIBE OFFLINE USERS
                    .observeOn(AndroidSchedulers.mainThread()).subscribe(topicMessage -> {
                toast(topicMessage.getPayload());
            });

            mStompClient.topic("/topic/onlineOfflineUsers").subscribeOn(Schedulers.io()) // SUBSCRIBE TO UPDATES USERS ON AND OFF
                    .observeOn(AndroidSchedulers.mainThread()).subscribe(topicMessage -> {
                toast(topicMessage.getPayload());
            });

            mStompClient.lifecycle().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(lifecycleEvent -> {
                        switch (lifecycleEvent.getType()) {
                            case OPENED:
                                toast("Stomp connection opened");
                                break;
                            case ERROR:
                                toast("Stomp connection error");
                                break;
                            case CLOSED:
                                toast("Stomp connection closed");
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }

    }// AKHIR FUNGSI KONEKSI WEBSOCKET

    public void kirimPesan() { // AWAL FUNGSI KIRIM PESAN BROADCAST / PRIVATE
        Log.d("onClick", "Tombol Kirim...");
        String strTuj = tujuan.getText().toString();
        String strPesan = pesan.getText().toString();

        mStompClient.send("/app/pesan.private." + strTuj, "{\"message\":\"" + strPesan + "\", " +
                    "\"profileId\":\"" + idProfile + "\"," + "\"destinationUser\":\"" + strTuj + "\"}").subscribe();
        tvStat.append("\nMe [private] : " + strPesan + ", to -> " + strTuj);

    } // AKHIR FUNGSI KIRIM PESAN BROADCAST / PRIVATE

    public void logout() { // AWAL FUNGSI LOGOUT

        mStompClient.send("/app/logout/" + idUaa).subscribe(); // remove ONLINE USER BY ID from redis
        mStompClient.disconnect();

    } // AKHIR FUNGSI LOGOUT

    private void toast(String text) { // TOAST
        Log.i("TOAST", text);
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    } // TOAST

}// END MAIN ACTIVITY