package uk.co.appsbystudio.geoshare.friends.friendsadapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;

import java.util.ArrayList;

import de.hdodenhof.circleimageview.CircleImageView;
import uk.co.appsbystudio.geoshare.R;
import uk.co.appsbystudio.geoshare.utils.ProfileUtils;
import uk.co.appsbystudio.geoshare.utils.firebase.listeners.GetUserFromDatabase;

public class FriendsPendingAdapter extends RecyclerView.Adapter<FriendsPendingAdapter.ViewHolder>{

    private final Context context;
    private final ArrayList userOutgoing;
    private final DatabaseReference databaseReference;

    public interface Callback {
        void onReject(String uid);
    }

    private final Callback callback;

    public FriendsPendingAdapter(Context context, ArrayList userOutgoing, DatabaseReference databaseReference, Callback callback) {
        this.context = context;
        this.userOutgoing = userOutgoing;
        this.databaseReference = databaseReference;
        this.callback = callback;
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup viewGroup, int viewType) {
        final View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.friends_pending_list_item, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        databaseReference.addListenerForSingleValueEvent(new GetUserFromDatabase(userOutgoing.get(position).toString(), holder.friend_name));

        if (!userOutgoing.isEmpty()) ProfileUtils.setProfilePicture(userOutgoing.get(position).toString(), holder.friends_pictures, context.getCacheDir().toString());

        holder.decline_request.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.onReject(userOutgoing.get(holder.getAdapterPosition()).toString());
            }
        });
    }

    @Override
    public int getItemCount() {
        return userOutgoing.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder{

        final TextView friend_name;
        final CircleImageView friends_pictures;
        final ImageView decline_request;

        ViewHolder(View itemView) {
            super(itemView);
            friend_name = itemView.findViewById(R.id.friend_name);
            friends_pictures = itemView.findViewById(R.id.friend_profile_image);
            decline_request = itemView.findViewById(R.id.friend_reject);
        }
    }
}
