package eu.davidea.flexibleadapter.items;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import java.util.List;
public abstract class AbstractFlexibleItem<VH extends RecyclerView.ViewHolder> implements IFlexible<VH> {
    protected boolean mEnabled = true;
    protected boolean mHidden = false;
    protected boolean mSelectable = true;
    protected boolean mDraggable = true;
    protected boolean mSwipeable = true;
    @Override public boolean isEnabled() { return mEnabled; }
    @Override public void setEnabled(boolean enabled) { mEnabled = enabled; }
    @Override public boolean isHidden() { return mHidden; }
    @Override public void setHidden(boolean hidden) { mHidden = hidden; }
    @Override public boolean isSelectable() { return mSelectable; }
    @Override public void setSelectable(boolean selectable) { mSelectable = selectable; }
    @Override public boolean isDraggable() { return mDraggable; }
    @Override public void setDraggable(boolean draggable) { mDraggable = draggable; }
    @Override public boolean isSwipeable() { return mSwipeable; }
    @Override public void setSwipeable(boolean swipeable) { mSwipeable = swipeable; }
    @Override public abstract int getLayoutRes();
    @Override public abstract VH createViewHolder(View view, FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>> adapter);
    @Override public abstract void bindViewHolder(FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>> adapter, VH holder, int position, List<Object> payloads);
}