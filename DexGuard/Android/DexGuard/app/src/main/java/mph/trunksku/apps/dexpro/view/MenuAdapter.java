package mph.trunksku.apps.dexpro.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import com.mph.dexprotect.R;

/**
 * Created by XUE on 2016/9/13.
 */
public class MenuAdapter extends BaseAdapter implements View.OnClickListener{

    private int[] images = new int[]{R.drawable.ic_launcher,
		R.drawable.ic_launcher,
		R.drawable.ic_launcher,
		R.drawable.ic_launcher,};
    private ItemEventListener listener;

    public void setListener(ItemEventListener listener) {
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return 4;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_menu, viewGroup, false);
        view.setOnClickListener(this);
        view.setTag(i);
        ImageView img = (ImageView) view.findViewById(R.id.image_view);
        img.setImageResource(images[i]);
        return view;
    }

    @Override
    public void onClick(View view) {
        if(listener!=null){
            listener.onEventNotify(view,(int)view.getTag());
        }
    }

}
