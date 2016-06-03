package uk.co.appsbystudio.geoshare.json;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import uk.co.appsbystudio.geoshare.friends.pages.FriendsFragment;
import uk.co.appsbystudio.geoshare.friends.pages.FriendsPendingFragment;

public class JSONObjectRequest extends AsyncTask<Void, Void, Boolean> {

    private String URL;
    private String status;
    private String pID;
    private boolean success = false;

    private final Context context;

    public JSONObjectRequest(Context context, String URL, String status, String pID) {
        this.URL = URL;
        this.context = context;
        this.status = status;
        this.pID = pID;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        HashMap<String, String> hashMap = new HashMap<>();
        hashMap.put("action", status);

        RequestQueue requestQueue = Volley.newRequestQueue(context);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PATCH, URL, new JSONObject(hashMap), null, null) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("REST_API_TOKEN", pID);
                headers.put("X-HTTP-Method-Override", "PATCH");
                headers.put("Content-Type", "application/json; charset=utf-8");
                headers.put("User-agent", System.getProperty("http.agent"));
                return headers;
            }

            @Override
            protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                System.out.println(response.statusCode);

                if (response.statusCode == 200) {
                    success = true;
                } else {
                    success = false;
                }

                return super.parseNetworkResponse(response);
            }
        };

        requestQueue.add(request);

        System.out.println(success);



        return success;
    }

    @Override
    protected void onPostExecute(final Boolean success) {
        if (success) {
            new FriendsFragment().refreshList();
            new FriendsPendingFragment().refreshList();
        }
    }
}