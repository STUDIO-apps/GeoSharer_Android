package uk.co.appsbystudio.geoshare;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageActivity;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;
import uk.co.appsbystudio.geoshare.friends.FriendsManager;
import uk.co.appsbystudio.geoshare.friends.friendsadapter.FriendsNavAdapter;
import uk.co.appsbystudio.geoshare.login.LoginActivity;
import uk.co.appsbystudio.geoshare.maps.MapsFragment;
import uk.co.appsbystudio.geoshare.utils.Connectivity;
import uk.co.appsbystudio.geoshare.utils.dialog.ProfilePictureOptions;
import uk.co.appsbystudio.geoshare.utils.firebase.UserInformation;
import uk.co.appsbystudio.geoshare.utils.ui.SettingsActivity;
import uk.co.appsbystudio.geoshare.utils.dialog.ShareOptions;
import uk.co.appsbystudio.geoshare.utils.services.TrackingService;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener, FriendsNavAdapter.Callback {
    private static final String TAG = "MainActivity";
    private static final boolean LOCAL_LOGV = true;

    private FirebaseAuth firebaseAuth;
    private FirebaseUser firebaseUser;
    private FirebaseAuth.AuthStateListener authStateListener;

    private DatabaseReference databaseReference;
    private DatabaseReference databaseFriendsRef;
    private DatabaseReference isTrackingRef;

    private StorageReference storageReference;

    private DrawerLayout drawerLayout;
    private DrawerLayout rightDrawer;
    private View header;
    private String userId;

    private TextView usernameTextView;

    private final ArrayList<String> uid = new ArrayList<>();
    private final HashMap<String, Boolean> hasTracking = new HashMap<>();

    private final MapsFragment mapsFragment = new MapsFragment();
    /*private final PlacesFragment placesFragment = new PlacesFragment();*/

    /*private final PlacesSearchFragment placesSearchFragment = new PlacesSearchFragment();*/

    private FriendsNavAdapter friendsNavAdapter;

    /*private FloatingActionButton search;
    private BottomSheetBehavior bottomSheetBehavior;*/

    private SharedPreferences settingsSharedPreferences;
    private SharedPreferences trackingPreferences;
    private SharedPreferences showOnMapPreferences;

    public static File cacheDir;

    public static final HashMap<String, Boolean> friendsId = new HashMap<>();
    public static final HashMap<String, Boolean> pendingId = new HashMap<>();

    public static final HashMap<String, String> friendNames = new HashMap<>();

    /*Animation animShowFab;*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settingsSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        settingsSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        trackingPreferences = getSharedPreferences("tracking", MODE_PRIVATE);
        showOnMapPreferences = getSharedPreferences("showOnMap", MODE_PRIVATE);

        boolean mobileNetwork = settingsSharedPreferences.getBoolean("mobile_network", true);

        if (mobileNetwork || Connectivity.isConnectedWifi(this)) {
            //Start tracking service if needed
            Thread thread = new Thread() {
                @Override
                public void run() {
                    SharedPreferences sharedPreferences = getSharedPreferences("tracking", MODE_PRIVATE);
                    Map<String, Boolean> shares = (Map<String, Boolean>) sharedPreferences.getAll();

                    for (Map.Entry<String, Boolean> hasShared : shares.entrySet()) {
                        if (hasShared.getValue()) {
                            Intent trackingService = new Intent(MainActivity.this, TrackingService.class);
                            startService(trackingService);
                            break;
                        }
                    }
                }
            };

            if (!TrackingService.isRunning) {
                thread.start();
            }
        }

        cacheDir = this.getCacheDir();

        //Firebase initialisation
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();

        userId = firebaseUser != null ? firebaseUser.getUid() : null;

        FirebaseDatabase database = FirebaseDatabase.getInstance();

        databaseReference = database.getReference();

        databaseFriendsRef = database.getReference("friends/" + userId);
        databaseFriendsRef.keepSynced(true);

        isTrackingRef = database.getReference("current_location/" + userId + "/tracking");
        isTrackingRef.keepSynced(true);

        storageReference = FirebaseStorage.getInstance().getReference();

        //sharedPreferences = getSharedPreferences("tracking", MODE_PRIVATE);

        /* HANDLES FOR VARIOUS VIEWS */
        drawerLayout = findViewById(R.id.drawer_layout);
        rightDrawer = findViewById(R.id.right_nav_drawer);
        NavigationView navigationView = findViewById(R.id.left_nav_view);
        RecyclerView rightNavigationView = findViewById(R.id.right_friends_drawer);
        if (rightNavigationView != null) rightNavigationView.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        if (rightNavigationView != null) rightNavigationView.setLayoutManager(layoutManager);

        //Get friends and populate right nav drawer
        getFriends();
        getTrackingStatus();


        friendsNavAdapter = new FriendsNavAdapter(this, rightNavigationView, uid, hasTracking, databaseReference, this);
        if (rightNavigationView != null) rightNavigationView.setAdapter(friendsNavAdapter);

        rightDrawer.setScrimColor(getResources().getColor(android.R.color.transparent));

        navigationView.getMenu().getItem(0).setChecked(true);
        header = navigationView.getHeaderView(0);
        CircleImageView profilePicture = header.findViewById(R.id.profile_image);

        usernameTextView = header.findViewById(R.id.username);

        /* RECENT APPS COLOR */
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            Bitmap bm = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
            ActivityManager.TaskDescription taskDesc;
            taskDesc = new ActivityManager.TaskDescription(getString(R.string.app_name), bm, ContextCompat.getColor(this, R.color.recent_color));
            this.setTaskDescription(taskDesc);
        }

        /* BOTTOM SHEET FRAGMENT SWAPPING */
        //getSupportFragmentManager().beginTransaction().add(R.id.bottom_sheet_container, placesSearchFragment).commit();

        /* LEFT NAV DRAWER FUNCTIONALITY/FRAGMENT SWAPPING */
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().add(R.id.content_frame_map, mapsFragment).commit();
        } else {
            getSupportFragmentManager().beginTransaction().show(mapsFragment).commit();
        }
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                item.setChecked(true);
                drawerLayout.closeDrawers();

                switch (item.getItemId()) {
                    case R.id.maps:
                        if (LOCAL_LOGV) Log.v(TAG, "Add maps fragment");
                        getSupportFragmentManager().beginTransaction().show(mapsFragment).commit();
                        return true;
                    case R.id.friends:
                        if (LOCAL_LOGV) Log.v(TAG, "Opening friends manager");
                        item.setChecked(false);
                        Intent intent = new Intent(MainActivity.this, FriendsManager.class);
                        startActivity(intent);
                        return true;
                    /*
                    case R.id.places:
                        if (LOCAL_LOGV) Log.v(TAG, "Add places fragment");
                        getSupportFragmentManager().beginTransaction().hide(mapsFragment).commit();
                        getFragmentManager().executePendingTransactions();
                        if(!placesFragment.isAdded()) getSupportFragmentManager().beginTransaction().replace(R.id.content_frame, placesFragment).commit();
                        return true;
                    //*/
                    case R.id.settings:
                        if (LOCAL_LOGV) Log.v(TAG, "Add settings fragment");
                        item.setChecked(false);
                        Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
                        startActivity(settingsIntent);
                        return true;
                    case R.id.logout:
                        if (LOCAL_LOGV) Log.v(TAG, "Calling logout()");
                        item.setChecked(false);
                        logout();
                        return true;
                    case R.id.feedback:
                        if (LOCAL_LOGV) Log.v(TAG, "Sending feedback");
                        item.setChecked(false);
                        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                        emailIntent.setType("text/plain");
                        emailIntent.setData(Uri.parse("mailto:support@appsbystudio.co.uk"));
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "GeoShare Feedback");
                        if (emailIntent.resolveActivity(getPackageManager()) != null) {
                            startActivity(Intent.createChooser(emailIntent, "Send email via"));
                        } else {
                            //TODO: Make toast.
                            if (LOCAL_LOGV) Log.v(TAG, "No email applications found on this device!");
                        }
                        return true;
                }
                return true;
            }
        });

        /* POPULATE LEFT NAV DRAWER HEADER FIELDS */
        profilePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (LOCAL_LOGV) Log.v(TAG, "Clicked on profile picture");
                profilePictureSettings();
            }
        });

        setDisplayName();

        //Check if users profile picture is stored in the cache
        File fileCheck = new File(getCacheDir() + "/" + userId + ".png");

        if (fileCheck.exists()) {
            //If file exists, set image view image as profile picture from storage
            //TODO: Allow for updating picture on different devices
            /* Could mean that this method will not work without getting the picture every time
                or adding a last updated section to the users profile picture
                and comparing with the date of the file created.
             */
            Bitmap imageBitmap = BitmapFactory.decodeFile(getCacheDir() + "/" + userId + ".png");
            ((CircleImageView) header.findViewById(R.id.profile_image)).setImageBitmap(imageBitmap);
        } else {
            //If the file doesn't exist, download from Firebase
            StorageReference profileRef = storageReference.child("profile_pictures/" + userId + ".png");
            profileRef.getFile(Uri.fromFile(fileCheck))
                    .addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                            Bitmap imageBitmap = BitmapFactory.decodeFile(getCacheDir() + "/" + userId + ".png");
                            ((CircleImageView) header.findViewById(R.id.profile_image)).setImageBitmap(imageBitmap);
                        }
            });
        }

        /*CoordinatorLayout coordinatorLayout = findViewById(R.id.coordinator);
        search = findViewById(R.id.searchLocationShare);

        //Set the animation for the FAB when the bottom sheet is made in/visible
        final Animation animHideFab = AnimationUtils.loadAnimation(this, R.anim.scale_down);
        animShowFab = AnimationUtils.loadAnimation(this, R.anim.scale_up);

        animHideFab.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                search.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        animShowFab.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                search.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        View bottomSheet = coordinatorLayout.findViewById(R.id.bottom_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                search.startAnimation(animHideFab);
                //getSupportFragmentManager().beginTransaction().hide(mapsFragment).commit();
                getSupportFragmentManager().beginTransaction().setCustomAnimations(R.anim.enter_up, R.anim.exit_down).replace(R.id.content_frame, placesSearchFragment).addToBackStack("").commit();
                //bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });*/

        Button showAllButton = findViewById(R.id.show_all_button);
        Button hideAllButton = findViewById(R.id.hide_all_button);

        showAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mapsFragment.setAllMarkersVisibility(true);
            }
        });

        hideAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mapsFragment.setAllMarkersVisibility(false);
            }
        });

        authStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser currentUser = firebaseAuth.getCurrentUser();
                if (currentUser == null) {
                    settingsSharedPreferences.edit().clear().apply();
                    trackingPreferences.edit().clear().apply();
                    showOnMapPreferences.edit().clear().apply();

                    Intent trackingService = new Intent(MainActivity.this, TrackingService.class);
                    stopService(trackingService);

                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                    startActivity(intent);
                    finish();
                }
            }
        };
    }

    private void setDisplayName() {
        if (firebaseUser != null) {
            String welcome = String.format(getResources().getString(R.string.welcome_user_header), firebaseUser.getDisplayName());
            usernameTextView.setText(welcome);

            settingsSharedPreferences.edit().putString("display_name", firebaseUser.getDisplayName()).apply();
        }
    }

    /* FIREBASE GET LIST OF FRIENDS */
    private void getFriends() {
        databaseFriendsRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                uid.add(dataSnapshot.getKey());
                if (!friendsId.containsKey(dataSnapshot.getKey())) friendsId.put(dataSnapshot.getKey(), true);
                getFriendsName(dataSnapshot.getKey());
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                uid.remove(dataSnapshot.getKey());
                if (friendsId.containsKey(dataSnapshot.getKey())) friendsId.remove(dataSnapshot.getKey());
                friendsNavAdapter.notifyDataSetChanged();
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void getFriendsName(final String friendId) {
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                UserInformation userInformation = dataSnapshot.child("users").child(friendId).getValue(UserInformation.class);
                if (userInformation != null) {
                    friendNames.put(friendId, userInformation.getName());
                    friendsNavAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void getTrackingStatus() {
        isTrackingRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                Boolean tracking = dataSnapshot.child("tracking").getValue(Boolean.class);
                if (tracking != null) {
                    hasTracking.put(dataSnapshot.getKey(), tracking);
                    friendsNavAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                Boolean tracking = dataSnapshot.child("tracking").getValue(Boolean.class);
                if (tracking != null) {
                    hasTracking.put(dataSnapshot.getKey(), tracking);
                    friendsNavAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                hasTracking.remove(dataSnapshot.getKey());
                friendsNavAdapter.notifyDataSetChanged();
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    /*public void showMapFragment() {
        if (LOCAL_LOGV) Log.v(TAG, "Showing map activity");
        getSupportFragmentManager().beginTransaction().show(mapsFragment).commit();
        navigationView.getMenu().getItem(0).setChecked(true);
    }*/

    /*public void backFromSearch() {
        if (LOCAL_LOGV) Log.v(TAG, "Back from searching");
        getSupportFragmentManager().beginTransaction().remove(placesSearchFragment).commit();
        search.startAnimation(animShowFab);
    }*/

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case 1:
                    String imageFileName = "profile_picture";
                    File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    File image = new File(storageDir, imageFileName + ".png");

                    CropImage.activity(Uri.fromFile(image))
                            .setGuidelines(CropImageView.Guidelines.ON)
                            .setAspectRatio(1, 1)
                            .setFixAspectRatio(true)
                            .start(this);
                    break;
                case 2:
                    Uri uri = data.getData();
                    if (uri != null)
                        CropImage.activity(uri)
                                .setGuidelines(CropImageView.Guidelines.ON)
                                .setAspectRatio(1, 1)
                                .setFixAspectRatio(true).start(this);
                    break;
            }
        }

        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE && data != null) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);

            if (resultCode == RESULT_OK) {
                final Uri resultUri = result.getUri();

                StorageReference profileRef = storageReference.child("profile_pictures/" + userId + ".png");
                profileRef.putFile(resultUri)
                        .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                Toast.makeText(MainActivity.this, "Picture uploaded", Toast.LENGTH_SHORT).show();
                                try {
                                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), resultUri);
                                    File file = new File(cacheDir, userId + ".png");
                                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);

                                    ((CircleImageView) header.findViewById(R.id.profile_image)).setImageBitmap(bitmap);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                            }
                        }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(MainActivity.this, "Hmm...Something went wrong.\nPlease check your internet connection and try again.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        firebaseAuth.addAuthStateListener(authStateListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (authStateListener != null) {
            firebaseAuth.removeAuthStateListener(authStateListener);
        }
    }

    /* FRAGMENTS CALL THIS TO OPEN NAV DRAWER */
    public void openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START);
    }

    public void openFriendsDrawer() {
        rightDrawer.openDrawer(GravityCompat.END);
    }

    /* CLICK FUNCTIONALITY FOR PROFILE PIC */
    private void profilePictureSettings() {
        android.app.FragmentManager fragmentManager = getFragmentManager();
        android.app.DialogFragment profileDialog = new ProfilePictureOptions();
        profileDialog.show(fragmentManager, "");
    }

    /* DIALOG FOR SENDING YOUR CURRENT LOCATION TO A FRIEND */
    public void sendLocationDialog(String name, String friendId) {
        if (LOCAL_LOGV) Log.v(TAG, "Opening send location dialog");
        Bundle arguments = new Bundle();
        arguments.putString("name", name);
        arguments.putString("friendId", friendId);
        arguments.putString("uid", userId);

        android.app.FragmentManager fragmentManager = getFragmentManager();
        android.app.DialogFragment friendDialog = new ShareOptions();
        friendDialog.setArguments(arguments);
        friendDialog.show(fragmentManager, "");
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            if (LOCAL_LOGV) Log.v(TAG, "Drawer closed");
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (rightDrawer.isDrawerOpen(GravityCompat.END)) {
            if (LOCAL_LOGV) Log.v(TAG, "Drawer closed");
            rightDrawer.closeDrawer(GravityCompat.END);
        } else {
            if (LOCAL_LOGV) Log.v(TAG, "Closing app");
            super.onBackPressed();
        }
    }

    /* FIREBASE AUTH LOG OUT */
    private void logout() {
        if (FirebaseAuth.getInstance() != null) {
            if (LOCAL_LOGV) Log.v(TAG, "Logging out");
            String token = FirebaseInstanceId.getInstance().getToken();
            if (token != null) {
                databaseReference.child("token").child(userId).child(token).removeValue();
            }
            FirebaseAuth.getInstance().signOut();
        } else {
            if (LOCAL_LOGV) Log.v(TAG, "Could not log out");
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals("mobile_network")) {
            boolean mobileNetwork = sharedPreferences.getBoolean("mobile_network", true);
            Intent trackingService = new Intent(this, TrackingService.class);
            if (mobileNetwork) {
                startService(trackingService);
            } else if (Connectivity.isConnectedMobile(this)){
                stopService(trackingService);
            }
        } else if (s.equals("display_name")) {
            String name = sharedPreferences.getString(s, "DEFAULT");
            databaseReference.child("users").child(userId).child("name").setValue(name);
            databaseReference.child("users").child(userId).child("caseFoldedName").setValue(name.toLowerCase());

            UserProfileChangeRequest profileChangeRequest =  new UserProfileChangeRequest.Builder().setDisplayName(name).build();

            firebaseUser.updateProfile(profileChangeRequest).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    setDisplayName();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        settingsSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void setMarkerHidden(String friendId, boolean visible) {
        mapsFragment.setMarkerVisibility(friendId, visible);
        showOnMapPreferences.edit().putBoolean(friendId, visible).apply();
    }

    @Override
    public void findOnMapClicked(String friendId) {
        mapsFragment.findFriendOnMap(friendId);
    }
}