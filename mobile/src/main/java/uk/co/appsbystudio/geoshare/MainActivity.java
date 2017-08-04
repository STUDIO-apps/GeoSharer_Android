package uk.co.appsbystudio.geoshare;

import android.app.ActivityManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.NavigationView;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import de.hdodenhof.circleimageview.CircleImageView;
import uk.co.appsbystudio.geoshare.friends.FriendsManagerFragment;
import uk.co.appsbystudio.geoshare.login.LoginActivity;
import uk.co.appsbystudio.geoshare.maps.MapsFragment;
import uk.co.appsbystudio.geoshare.places.PlacesFragment;
import uk.co.appsbystudio.geoshare.settings.FriendDialog;
import uk.co.appsbystudio.geoshare.settings.ProfilePictureOptions;
import uk.co.appsbystudio.geoshare.settings.SettingsFragment;
import uk.co.appsbystudio.geoshare.settings.ShareALocationDialog;
import uk.co.appsbystudio.geoshare.settings.ShareOptions;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private View header;

    private final MapsFragment mapsFragment = new MapsFragment();
    private final FriendsManagerFragment friendsManagerFragment = new FriendsManagerFragment();
    private final PlacesFragment placesFragment = new PlacesFragment();
    private final SettingsFragment settingsFragment = new SettingsFragment();

    NavigationView navigationView;

    private Bitmap bitmap;
    private File imageFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* HANDLES FOR VARIOUS VIEWS */
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        navigationView = (NavigationView) findViewById(R.id.left_nav_view);
        RecyclerView rightNavigationView = (RecyclerView) findViewById(R.id.right_friends_drawer);
        if (rightNavigationView != null) {
            rightNavigationView.setHasFixedSize(true);
        }
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        if (rightNavigationView != null) rightNavigationView.setLayoutManager(layoutManager);

        //TODO: Get friends for right nav drawer

        navigationView.getMenu().getItem(0).setChecked(true);
        header = navigationView.getHeaderView(0);
        CircleImageView profilePicture = (CircleImageView) header.findViewById(R.id.profile_image);

        /* RECENT APPS COLOR */
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            Bitmap bm = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
            ActivityManager.TaskDescription taskDesc;
            taskDesc = new ActivityManager.TaskDescription(getString(R.string.app_name), bm, ContextCompat.getColor(this, R.color.recent_color));
            this.setTaskDescription(taskDesc);
        }

        /* LEFT NAV DRAWER FUNCTIONALITY/FRAGMENT SWAPPING */
        getSupportFragmentManager().beginTransaction().add(R.id.content_frame_map, mapsFragment).commit();
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                item.setChecked(true);
                drawerLayout.closeDrawers();

                switch (item.getItemId()) {
                    case R.id.maps:
                        getSupportFragmentManager().beginTransaction().remove(settingsFragment).commit();
                        getSupportFragmentManager().beginTransaction().remove(friendsManagerFragment).commit();
                        getSupportFragmentManager().beginTransaction().remove(placesFragment).commit();
                        getSupportFragmentManager().beginTransaction().show(mapsFragment).commit();
                        return true;
                    case R.id.friends:
                        getSupportFragmentManager().beginTransaction().hide(mapsFragment).commit();
                        getFragmentManager().executePendingTransactions();
                        if(!friendsManagerFragment.isAdded()) getSupportFragmentManager().beginTransaction().replace(R.id.content_frame, friendsManagerFragment).commit();
                        return true;
                    case R.id.places:
                        getSupportFragmentManager().beginTransaction().hide(mapsFragment).commit();
                        getFragmentManager().executePendingTransactions();
                        if(!placesFragment.isAdded()) getSupportFragmentManager().beginTransaction().replace(R.id.content_frame, placesFragment).commit();
                        return true;
                    case R.id.settings:
                        getSupportFragmentManager().beginTransaction().hide(mapsFragment).commit();
                        getFragmentManager().executePendingTransactions();
                        if(!settingsFragment.isAdded()) getSupportFragmentManager().beginTransaction().replace(R.id.content_frame, settingsFragment).commit();
                        return true;
                    case R.id.logout:
                        item.setChecked(false);
                        logout();
                        return true;
                    case R.id.feedback:
                        item.setChecked(false);
                        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                        emailIntent.setType("text/plain");
                        emailIntent.setData(Uri.parse("mailto:support@appsbystudio.co.uk"));
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "GeoShare Feedback");
                        if (emailIntent.resolveActivity(getPackageManager()) != null) {
                            startActivity(Intent.createChooser(emailIntent, "Send email via"));
                        } else {
                            //TODO: Make toast.
                            System.out.println("No email applications found on this device!");
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
                profilePictureSettings();
            }
        });

        TextView usernameTextView = (TextView) header.findViewById(R.id.username);
        String welcome = String.format(getResources().getString(R.string.welcome_user_header), "Get user name here!");
        usernameTextView.setText(welcome);

    }

    public void showMapFragment() {
        getSupportFragmentManager().beginTransaction().show(mapsFragment).commit();
        navigationView.getMenu().getItem(0).setChecked(true);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 && resultCode == RESULT_OK) {
            bitmap = (Bitmap) data.getExtras().get("data");

            imageFile = new File(this.getCacheDir(), "profile");

            try {
                FileOutputStream stream = new FileOutputStream(imageFile);
                bitmap.compress(Bitmap.CompressFormat.PNG, 1, stream);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            Uri uri = Uri.fromFile(imageFile);

            CropImage.activity(uri).setGuidelines(CropImageView.Guidelines.ON).setAspectRatio(1, 1).setFixAspectRatio(true).start(this);

        } else if (requestCode == 2 && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            CropImage.activity(uri).setGuidelines(CropImageView.Guidelines.ON).setAspectRatio(1, 1).setFixAspectRatio(true).start(this);
        } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE && data != null) {
            CropImage.ActivityResult activityResult = CropImage.getActivityResult(data);
            Uri uri = activityResult.getUri();

            try {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                imageFile = new File(this.getCacheDir(), "profile");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE && data != null) {
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(imageFile);
                if (bitmap != null) {
                    //bitmap = scaleImage(bitmap);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 50, fileOutputStream);
                }

                fileOutputStream.close();

                //TODO: Upload image

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /* FRAGMENTS CALL THIS TO OPEN NAV DRAWER */
    public void openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START);
    }

    public void openFriendsDrawer() {
        drawerLayout.openDrawer(GravityCompat.END);
    }

    /* CLICK FUNCTIONALITY FOR PROFILE PIC */
    private void profilePictureSettings() {
        android.app.FragmentManager fragmentManager = getFragmentManager();
        android.app.DialogFragment profileDialog = new ProfilePictureOptions();
        profileDialog.show(fragmentManager, "");
    }

    public void sendLocationDialog(String name) {
        Bundle arguments = new Bundle();
        arguments.putString("name", name);

        android.app.FragmentManager fragmentManager = getFragmentManager();
        android.app.DialogFragment friendDialog = new ShareOptions();
        friendDialog.setArguments(arguments);
        friendDialog.show(fragmentManager, "");
    }

    public void friendsDialog(String name) {
        Bundle arguments = new Bundle();
        arguments.putString("name", name);

        android.app.FragmentManager fragmentManager = getFragmentManager();
        android.app.DialogFragment friendDialog = new FriendDialog();
        friendDialog.setArguments(arguments);
        friendDialog.show(fragmentManager, "");
    }

    public void shareALocation() {
        android.app.FragmentManager fragmentManager = getFragmentManager();
        android.app.DialogFragment shareALocationDialog = new ShareALocationDialog();
        shareALocationDialog.show(fragmentManager, "");
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void logout() {
        //TODO: logout
        loginReturn();
    }

    private void loginReturn() {
        Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
        startActivity(intent);
        this.finish();
    }
}