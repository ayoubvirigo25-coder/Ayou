package eu.davidea.viewholders;
import android.view.View;
import eu.davidea.flexibleadapter.FlexibleAdapter;
@SuppressWarnings("rawtypes")
public abstract class ExpandableViewHolder extends FlexibleViewHolder {
    public ExpandableViewHolder(View view, FlexibleAdapter adapter) { super(view, adapter); }
    public ExpandableViewHolder(View view, FlexibleAdapter adapter, boolean stickyHeader) { super(view, adapter, stickyHeader); }
}