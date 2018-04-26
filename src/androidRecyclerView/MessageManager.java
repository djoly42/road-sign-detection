package androidRecyclerView;

//import com.couchbase.lite.android.AndroidContext;

/**
 * Created by daniel on 22/04/18.
 */

public class MessageManager {

  private static final String TAG = "TAG";
  public static String lastMessage;


  public static String getLastMessage() {
    return lastMessage;
  }

  public static void setLastMessage(String lastMessage) {
    MessageManager.lastMessage = lastMessage;
  }


  public MessageManager() {
  }

  public boolean doSend(String message) {
    if (lastMessage == null){
      lastMessage = message;
      return true;
    }
    if (!lastMessage.equals(message)){
      lastMessage = message;
      return true;
    }
    return false;
  }

}
