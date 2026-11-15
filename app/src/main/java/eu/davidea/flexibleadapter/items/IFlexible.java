package eu.davidea.flexibleadapter.items;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import java.util.List;
public interface IFlexible<VH extends RecyclerView.ViewHolder> {
    boolean isEnabled();
    void setEnabled(boolean enabled);
    boolean isHidden();
    void setHidden(boolean hidden);
    boolean isSelectable();
    void setSelectable(boolean selectable);
    boolean isDraggable();
    void setDraggable(boolean draggable);
    boolean isSwipeable();
    void setSwipeable(boolean swipeable);
    int getLayoutRes();
    VH createViewHolder(View view, FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>> adapter);
    void bindViewHolder(FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>> adapter, VH holder, int position, List<Object> payloads);
}