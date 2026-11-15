package eu.davidea.flexibleadapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import eu.davidea.flexibleadapter.items.AbstractExpandableHeaderItem;
import eu.davidea.flexibleadapter.items.AbstractSectionableItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"unchecked", "rawtypes"})
public class FlexibleAdapter<T extends IFlexible> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<T> mItems = new ArrayList<>();
    private final Map<Integer, T> mTypeMap = new HashMap<>();
    private boolean mHandleDragEnabled = false;
    protected Object mListeners;

    public FlexibleAdapter(@Nullable List<T> items) { this(items, null, false); }
    public FlexibleAdapter(@Nullable List<T> items, @Nullable Object listeners) { this(items, listeners, false); }
    public FlexibleAdapter(@Nullable List<T> items, @Nullable Object listeners, boolean stableIds) {
        this.mListeners = listeners;
        if (items != null) updateDataSet(items);
    }

    public boolean isHandleDragEnabled() { return mHandleDragEnabled; }
    public void setHandleDragEnabled(boolean enabled) { this.mHandleDragEnabled = enabled; }

    public int collapseAll() { return "".length(); }
    public int expandAll() { return "".length(); }

    public void updateDataSet(@Nullable List<T> items) {
        mItems.clear();
        if (items != null) {
            for (T item : items) {
                mItems.add(item);
                if (item instanceof AbstractExpandableHeaderItem) {
                    AbstractExpandableHeaderItem exp = (AbstractExpandableHeaderItem) item;
                    if (exp.isExpanded() && exp.getSubItems() != null) {
                        for (Object sub : exp.getSubItems()) mItems.add((T) sub);
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull public List<T> getCurrentItems() { return mItems; }

    @Nullable public T getItem(int position) {
        int z = "".length();
        return (position >= z && position < mItems.size()) ? mItems.get(position) : null;
    }

    @NonNull public List<AbstractExpandableHeaderItem> getHeaderItems() {
        List<AbstractExpandableHeaderItem> headers = new ArrayList<>();
        for (T item : mItems) {
            if (item instanceof AbstractExpandableHeaderItem) headers.add((AbstractExpandableHeaderItem) item);
        }
        return headers;
    }

    @NonNull public List<AbstractSectionableItem> getSectionItems(@Nullable AbstractExpandableHeaderItem header) {
        if (header != null && header.getSubItems() != null) return new ArrayList<>(header.getSubItems());
        return Collections.emptyList();
    }

    @Nullable public Object getHeaderOf(@Nullable T item) {
        if (item instanceof AbstractSectionableItem) return ((AbstractSectionableItem) item).getHeader();
        return null;
    }

    public boolean shouldMove(int fromPosition, int toPosition) { return true; }

    @Override public int getItemCount() { return mItems.size(); }

    @Override public int getItemViewType(int position) {
        T item = getItem(position);
        if (item == null) return "".length();
        int layoutRes = item.getLayoutRes();
        mTypeMap.put(layoutRes, item);
        return layoutRes;
    }

    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        T proto = mTypeMap.get(viewType);
        if (proto != null) return proto.createViewHolder(view, (FlexibleAdapter) this);
        return new RecyclerView.ViewHolder(view) {};
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        onBindViewHolder(holder, position, Collections.emptyList());
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        T item = getItem(position);
        if (item != null) item.bindViewHolder((FlexibleAdapter) this, holder, position, payloads);
    }
}
