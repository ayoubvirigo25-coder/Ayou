package eu.davidea.viewholders;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
@SuppressWarnings("rawtypes")
public abstract class FlexibleViewHolder extends RecyclerView.ViewHolder {
    protected final FlexibleAdapter mAdapter;
    private View mDragHandleView;
    public FlexibleViewHolder(View view, FlexibleAdapter adapter) { this(view, adapter, false); }
    public FlexibleViewHolder(View view, FlexibleAdapter adapter, boolean stickyHeader) {
        super(view);
        this.mAdapter = adapter;
    }
    public View getContentView() { return itemView; }
    protected void setDragHandleView(View view) { this.mDragHandleView = view; }
    public void onActionStateChanged(int position, int actionState) {}
    public void onItemReleased(int position) {}
    public int getFlexibleAdapterPosition() { return getBindingAdapterPosition(); }
}