package androidRecyclerView;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.tensorflow.demo.R;

import java.util.List;

//import org.tensorflow.demo.ClassifierActivity.R;

//import com.javacodegeeks.R;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

  private List<Message> messageList;

  public static final int SENDER = 0;
  public static final int RECIPIENT = 1;

  public MessageAdapter(Context context, List<Message> messages) {
    messageList = messages;
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    public TextView mTextView;

    public ViewHolder(LinearLayout v) {
      super(v);
      mTextView = (TextView) v.findViewById(R.id.text);
    }
  }

  public void remove(int pos) {
    int position = pos;
    messageList.remove(position);
    notifyItemRemoved(position);
    notifyItemRangeChanged(position, messageList.size());

  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    Log.d("Message adapter", "message supprime lors de l import");
    return null;
  }

  @Override
  public void onBindViewHolder(ViewHolder holder, final int position) {
    holder.mTextView.setText(messageList.get(position).getMessage());
    holder.mTextView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        remove(position);
      }
    });
  }

  @Override
  public int getItemCount() {
    return messageList.size();
  }

  @Override
  public int getItemViewType(int position) {
    Message message = messageList.get(position);

    if (message.getSenderName().equals("Me")) {
      return SENDER;
    } else {
      return RECIPIENT;
    }

  }

}