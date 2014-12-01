package org.creativecommons.thelist;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.creativecommons.thelist.adapters.CategoryListAdapter;
import org.creativecommons.thelist.adapters.CategoryListItem;
import org.creativecommons.thelist.utils.ApiConstants;
import org.creativecommons.thelist.utils.RequestMethods;
import org.creativecommons.thelist.utils.SharedPreferencesMethods;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


public class CategoryListActivity extends Activity {
    public static final String TAG = CategoryListActivity.class.getSimpleName();
    //Helper Methods
    RequestMethods requestMethods = new RequestMethods(this);
    SharedPreferencesMethods sharedPreferencesMethods = new SharedPreferencesMethods(this);

    //GET Request
    protected JSONObject mCategoryData;
    protected JSONArray mJsonCategories;

    //PUT request (if user is logged in)
    protected JSONObject mPutResponse;

    //Handle Data
    private List<CategoryListItem> mCategoryList = new ArrayList<CategoryListItem>();
    protected CategoryListAdapter adapter;

    //UI Elements
    protected ProgressBar mProgressBar;
    protected Button mNextButton;
    protected ListView mListView;

    // --------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_list);

        //Load UI Elements
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mNextButton = (Button) findViewById(R.id.nextButton);
        mNextButton.setVisibility(View.INVISIBLE);
        mListView = (ListView)findViewById(R.id.list);

        //Set List Adapter
        adapter = new CategoryListAdapter(this,mCategoryList);
        mListView.setAdapter(adapter);
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        //Category Selection
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                ImageView checkmarkView = (ImageView)view.findViewById(R.id.checkmark);

                if(mListView.isItemChecked(position)) {
                    checkmarkView.setVisibility(View.VISIBLE);
                } else {
                    checkmarkView.setVisibility(View.INVISIBLE);
                }

                //Count how many items are checked: if at least 3, show Next Button
                SparseBooleanArray positions = mListView.getCheckedItemPositions();
                int length = positions.size();
                int ItemsChecked = 0;
                if (positions != null) {

                    for (int i = 0; i < length; i++) {
                        if (positions.get(positions.keyAt(i))) {
                            ItemsChecked++;
                        }
                    }
                }
                if (ItemsChecked >= 3) {
                    mNextButton.setVisibility(View.VISIBLE);
                }
                else {
                    mNextButton.setVisibility(View.INVISIBLE);
                }
            }
        });

        //If Network Connection is available, Execute getDataTask
        if(requestMethods.isNetworkAvailable()) {
            mProgressBar.setVisibility(View.VISIBLE);
            getCategoriesRequest();
        }
        else {
            Toast.makeText(this, "Network is unavailable", Toast.LENGTH_LONG).show();
        }

        //Next Button: handle User’s Category Preferences
        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //If logged in, put to user profile, else create sharedPreference
                if(requestMethods.isLoggedIn()) {
                    storeCategoriesRequest();
                } else {
                    SparseBooleanArray positions = mListView.getCheckedItemPositions();
                    int length = positions.size();
                    //Array of user selected categories
                    List<Integer> userCategories = new ArrayList<Integer>();

                    for(int i = 0; i < length; i++) {
                        int itemPosition = positions.keyAt(i);
                        CategoryListItem item = (CategoryListItem) mListView.getItemAtPosition(itemPosition);
                        int id = item.getCategoryID();
                        userCategories.add(id);
                    }
                    //Save user categories to shared preferences
                    sharedPreferencesMethods.SaveSharedPreference
                            (sharedPreferencesMethods.CATEGORY_PREFERENCE,
                                    sharedPreferencesMethods.CATEGORY_PREFERENCE_KEY, userCategories.toString());
                }
                //Navigate to Random Activity
                Intent intent = new Intent(CategoryListActivity.this, RandomActivity.class);
                startActivity(intent);
            }
        });
    } //onCreate

    //UPDATE LIST WITH CONTENT
    private void updateList() {
        mProgressBar.setVisibility(View.INVISIBLE);
        if (mCategoryData == null) {
            requestMethods.updateDisplayForError();
        }
        else {
            try {
                mJsonCategories = mCategoryData.getJSONArray(ApiConstants.RESPONSE_CONTENT);

                for(int i = 0; i<mJsonCategories.length(); i++) {
                    JSONObject jsonSingleCategory = mJsonCategories.getJSONObject(i);
                    CategoryListItem categoryListItem = new CategoryListItem();
                    categoryListItem.setCategoryName(jsonSingleCategory.getString(ApiConstants.CATEGORY_NAME));
                    categoryListItem.setCategoryID(jsonSingleCategory.getInt(ApiConstants.CATEGORY_ID));

                    //Adding to array of List Items
                    mCategoryList.add(categoryListItem);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Exception Caught: ", e);
            }
            adapter.notifyDataSetChanged();
        }
    } //updateList

    //GET Categories from API
    private void getCategoriesRequest() {
        RequestQueue queue = Volley.newRequestQueue(this);
        //Genymotion Emulator
        String url = "http://10.0.3.2:3000/api/category";
        //Android Default Emulator
        //String url = "http://10.0.2.2:3000/api/category";

        JsonObjectRequest getCategoriesRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        //Handle Data
                        mCategoryData = response;
                        updateList();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse (VolleyError error){
                requestMethods.updateDisplayForError();
            }
        });
        queue.add(getCategoriesRequest);
    } //getCategoriesRequest


    //PUT REQUEST: Add category preferences to DB
    private void storeCategoriesRequest() {
        RequestQueue queue = Volley.newRequestQueue(this);
        String userID = requestMethods.getUserID();
        //Genymotion Emulator
        String url = "http://10.0.3.2:3000/api/user/" + userID;
        //Android Default Emulator
        //String url = "http://10.0.2.2:3000/api/user";

        //Retrieve User category preferences
        JSONArray userPreferences = sharedPreferencesMethods.RetrieveSharedPreference
                (sharedPreferencesMethods.CATEGORY_PREFERENCE,
                        sharedPreferencesMethods.CATEGORY_PREFERENCE_KEY);

        //Create Object to send
        JSONObject jso = new JSONObject();
        try {
            jso.put(ApiConstants.USER_CATEGORIES, userPreferences);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }

        //Volley Request
        JsonObjectRequest postCategoriesRequest = new JsonObjectRequest(Request.Method.PUT, url, jso,
                new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            //TODO: Check response code + display error
//                            if(responseCode != 200), get response data + show error

                            //Handle Data
                            mPutResponse = response.getJSONObject(ApiConstants.RESPONSE_CONTENT);
                            Log.v(TAG, mPutResponse.toString());

                            mProgressBar.setVisibility(View.INVISIBLE);

                        } catch (JSONException e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse (VolleyError error){
                requestMethods.updateDisplayForError();
            }
        });
        queue.add(postCategoriesRequest);
    } //storeCategoriesRequest


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_category_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}


