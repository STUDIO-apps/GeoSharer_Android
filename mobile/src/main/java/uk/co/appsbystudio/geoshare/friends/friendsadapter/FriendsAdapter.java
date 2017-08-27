package uk.co.appsbystudio.geoshare.friends.friendsadapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import de.hdodenhof.circleimageview.CircleImageView;
import uk.co.appsbystudio.geoshare.MainActivity;
import uk.co.appsbystudio.geoshare.R;
import uk.co.appsbystudio.geoshare.friends.pages.FriendInfoActivity;
import uk.co.appsbystudio.geoshare.utils.UserInformation;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder>{
    private final Context context;
    private final ArrayList userId;
    private final DatabaseReference databaseReference;

    public FriendsAdapter(Context context, ArrayList userId, DatabaseReference databaseReference) {
        this.context = context;
        this.userId = userId;
        this.databaseReference = databaseReference;
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup viewGroup, int viewType) {
        final View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.friends_list_item, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                UserInformation userInformation = dataSnapshot.child("users").child(userId.get(holder.getAdapterPosition()).toString()).getValue(UserInformation.class);
                assert userInformation != null;
                holder.friend_name.setText(userInformation.getName());
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        //TODO: Set friends profile picture
        if (!userId.isEmpty()) {
            File fileCheck = new File(context.getCacheDir() + "/" + userId.get(position) + ".png");

            if (fileCheck.exists()) {
                Bitmap imageBitmap = BitmapFactory.decodeFile(context.getCacheDir() + "/" + userId.get(position) + ".png");
                holder.friends_pictures.setImageBitmap(imageBitmap);
            } else {
                StorageReference storageReference = FirebaseStorage.getInstance().getReference();
                StorageReference profileRef = storageReference.child("profile_pictures/" + userId.get(position) + ".png");
                profileRef.getFile(Uri.fromFile(new File(context.getCacheDir() + "/" + userId.get(position) + ".png")))
                        .addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                                Bitmap imageBitmap = BitmapFactory.decodeFile(context.getCacheDir() + "/" + userId.get(holder.getAdapterPosition()) + ".png");
                                holder.friends_pictures.setImageBitmap(imageBitmap);
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                holder.friends_pictures.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_profile_picture));
                            }
                        });
            }
        }

        holder.more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PopupMenu popupMenu = new PopupMenu(context, view);
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.removeFriend:
                                //TODO: Remove friend
                                return true;
                            case R.id.showProfile:
                                Intent intent = new Intent(context, FriendInfoActivity.class);
                                context.startActivity(intent);
                                return true;
                            default:
                                return false;
                        }
                    }
                });
                MenuInflater menuInflater = popupMenu.getMenuInflater();
                menuInflater.inflate(R.menu.friend_menu, popupMenu.getMenu());
                popupMenu.show();
            }
        });

        holder.item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, FriendInfoActivity.class);
                context.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return userId.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder{

        final TextView friend_name;
        final CircleImageView friends_pictures;
        final ImageView more;
        final RelativeLayout item;

        ViewHolder(View itemView) {
            super(itemView);
            friend_name = (TextView) itemView.findViewById(R.id.friend_name);
            friends_pictures = (CircleImageView) itemView.findViewById(R.id.friend_profile_image);
            more = (ImageView) itemView.findViewById(R.id.more);
            item = (RelativeLayout) itemView.findViewById(R.id.item);
        }
    }
}
