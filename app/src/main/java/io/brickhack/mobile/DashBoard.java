package io.brickhack.mobile;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;

import org.json.JSONException;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


public class DashBoard extends AppCompatActivity {

    private static final String SHARED_PREFERENCES_NAME = "BrickHackPreference";
    private static final String AUTH_STATE = "AUTH_STATE";
    private static final String SERVICE_CONFIGURATION = "SERVICE_CONFIGURATION";

    AuthState authState;
    AuthorizationServiceConfiguration serviceConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        authState = restoreAuthState();
        serviceConfig = restoreServiceConfig();

        Button wristband = findViewById(R.id.id_wristband);
        Button logout = findViewById(R.id.button_logout);
        Toolbar toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);

        fetchTodayStats();

        wristband.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent theIntent = new Intent(DashBoard.this, Wristband.class);
                startActivity(theIntent);
            }
        });

        logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearAuthState();
                Intent moveToAuthentication = new Intent(DashBoard.this, Authentication.class);
                startActivity(moveToAuthentication);
            }
        });
    }

    // Prevents user from returning to login screen without pressing the logout button
    @Override
    public void onBackPressed(){
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Nullable
    private AuthState restoreAuthState() {
        String jsonString = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(AUTH_STATE, null);
        if (!TextUtils.isEmpty(jsonString)) {
            try {
                return AuthState.jsonDeserialize(jsonString);
            } catch (JSONException jsonException) {
                // should never happen
            }
        }
        return null;
    }

    @Nullable
    private AuthorizationServiceConfiguration restoreServiceConfig() {
        String jsonString = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(SERVICE_CONFIGURATION, null);
        if (!TextUtils.isEmpty(jsonString)) {
            try {
                return AuthorizationServiceConfiguration.fromJson(jsonString);
            } catch (JSONException jsonException) {
                // should never happen
            }
        }
        return null;
    }

    private void clearAuthState() {
        getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(AUTH_STATE)
                .apply();
    }

    private void fetchTodayStats(){

        final TextView confirmations = findViewById(R.id.num_confirmations);
        final TextView applications = findViewById(R.id.num_applications);
        final TextView denials = findViewById(R.id.num_denials);

        AuthorizationService authorizationService = new AuthorizationService(this);
        authState.performActionWithFreshTokens(authorizationService, new AuthState.AuthStateAction() {
            @Override
            public void execute(@Nullable final String accessToken, @Nullable String idToken, @Nullable AuthorizationException ex) {

                GsonBuilder gsonBuilder = new GsonBuilder();
                gsonBuilder.setLenient();
                Gson gson = gsonBuilder.create();

                HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                logging.setLevel(HttpLoggingInterceptor.Level.BODY);

                OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

                clientBuilder.addInterceptor(new Interceptor() {
                    @Override
                    public okhttp3.Response intercept(Chain chain) throws IOException {
                        Request newRequest  = chain.request().newBuilder()
                                .addHeader("Authorization", "Bearer " + accessToken)
                                .build();
                        return chain.proceed(newRequest);
                    }
                });

                Retrofit retrofit = new Retrofit.Builder()
                        .baseUrl("https://brickhack.io")
                        .addConverterFactory(GsonConverterFactory.create(gson))
                        .client(clientBuilder.build())
                        .build();

                BrickHackAPI brickHackAPI = retrofit.create(BrickHackAPI.class);
                Call<JsonElement> call = brickHackAPI.getStats();

                call.enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                        if(response.isSuccessful()){
                            try{
                                if(response.body() instanceof JsonObject){
                                    JsonElement confirmations_raw = response.body().getAsJsonObject().get("Confirmations");
                                    confirmations.setText(confirmations_raw.toString());

                                    JsonElement applications_raw = response.body().getAsJsonObject().get("Applications");
                                    applications.setText(applications_raw.toString());

                                    JsonElement denials_raw = response.body().getAsJsonObject().get("Denials");
                                    denials.setText(denials_raw.toString());
                                }else{
                                    AlertDialog.Builder builder = new AlertDialog.Builder(DashBoard.this);
                                    builder.setMessage(R.string.error_dialog_message)
                                            .setTitle(R.string.error_dialog_title)
                                            .setCancelable(false)
                                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int id) {
                                                    //clear dialog
                                                }
                                            });
                                    AlertDialog dialog = builder.create();
                                    dialog.show();
                                }
                            }catch (NullPointerException e){
                                System.out.println(e);
                            }

                        }
                    }

                    @Override
                    public void onFailure(Call<JsonElement> call, Throwable t) {
                        System.out.println("ERROR: " + t.getMessage());
                    }
                });
            }
        });
    }
}
