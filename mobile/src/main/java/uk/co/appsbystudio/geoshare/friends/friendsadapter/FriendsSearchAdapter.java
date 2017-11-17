package uk.co.appsbystudio.geoshare.friends.friendsadapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.ArrayList;

import de.hdodenhof.circleimageview.CircleImageView;
import uk.co.appsbystudio.geoshare.MainActivity;
import uk.co.appsbystudio.geoshare.R;

public class FriendsSearchAdapter extends RecyclerView.Adapter<FriendsSearchAdapter.ViewHolder>{

    private final Context context;
    private final ArrayList namesArray;
    private final ArrayList userId;

    public interface Callback{
        void onSendRequest(String friendId);
    }

    private final Callback callback;

    public FriendsSearchAdapter(Context context, ArrayList namesArray, ArrayList userId, Callback callback) {
        this.context = context;
        this.namesArray = namesArray;
        this.userId = userId;
        this.callback = callback;
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup viewGroup, final int viewType) {
        final View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.friends_search_item, viewGroup, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.friend_name.setText(namesArray.get(position).toString());

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

        if (MainActivity.friendsId.containsKey(userId.get(position).toString())) {
            holder.sendRequestButton.setImageDrawable(context.getDrawable(R.drawable.ic_person_white_24dp));
            holder.sendRequestButton.setImageTintList(ColorStateList.valueOf(context.getResources().getColor(R.color.colorPrimary)));
        } else if (MainActivity.pendingId.containsKey(userId.get(position).toString())) {
            holder.sendRequestButton.setImageDrawable(context.getDrawable(R.drawable.ic_person_white_24dp));
            holder.sendRequestButton.setImageTintList(ColorStateList.valueOf(context.getResources().getColor(android.R.color.darker_gray)));
        } else {
            holder.sendRequestButton.setImageDrawable(context.getDrawable(R.drawable.ic_send_black_24dp));
            holder.sendRequestButton.setImageTintList(ColorStateList.valueOf(context.getResources().getColor(android.R.color.darker_gray)));
            holder.sendRequestButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    callback.onSendRequest(userId.get(holder.getAdapterPosition()).toString());
                    holder.sendRequestButton.setImageDrawable(context.getDrawable(R.drawable.ic_person_white_24dp));
                    holder.sendRequestButton.setImageTintList(ColorStateList.valueOf(context.getResources().getColor(android.R.color.darker_gray)));
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return namesArray.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder{

        final TextView friend_name;
        final CircleImageView friends_pictures;
        final ImageButton sendRequestButton;
        final RelativeLayout item;

        ViewHolder(View itemView) {
            super(itemView);
            friend_name = itemView.findViewById(R.id.friend_name);
            friends_pictures = itemView.findViewById(R.id.friend_profile_image);
            sendRequestButton = itemView.findViewById(R.id.sendRequestButton);
            item = itemView.findViewById(R.id.item);
        }
    }
}
