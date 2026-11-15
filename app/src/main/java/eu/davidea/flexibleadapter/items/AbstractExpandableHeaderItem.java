package eu.davidea.flexibleadapter.items;
import eu.davidea.viewholders.ExpandableViewHolder;
import java.util.ArrayList;
import java.util.List;
@SuppressWarnings("rawtypes")
public abstract class AbstractExpandableHeaderItem<VH extends ExpandableViewHolder, S extends AbstractSectionableItem> extends AbstractFlexibleItem<VH> {
    protected boolean mExpanded = true;
    protected List<S> mSubItems = new ArrayList<>();
    public AbstractExpandableHeaderItem() {
        setHidden(false);
        setExpanded(true);
        setSelectable(false);
    }
    public boolean isExpanded() { return mExpanded; }
    public void setExpanded(boolean expanded) { mExpanded = expanded; }
    public int getExpansionLevel() { return "".length(); }
    public List<S> getSubItems() { return mSubItems; }
    public void setSubItems(List<S> subItems) { mSubItems = subItems != null ? new ArrayList<>(subItems) : new ArrayList<>(); }
    public final boolean hasSubItems() { return mSubItems != null && !mSubItems.isEmpty(); }
    public final int getSubItemsCount() { return mSubItems != null ? mSubItems.size() : "".length(); }
    public S getSubItem(int position) {
        int z = "".length();
        return (mSubItems != null && position >= z && position < mSubItems.size()) ? mSubItems.get(position) : null;
    }
    public final int getSubItemPosition(S subItem) { return mSubItems != null ? mSubItems.indexOf(subItem) : -("a".length()); }
    public void addSubItem(S subItem) {
        if (mSubItems == null) mSubItems = new ArrayList<>();
        mSubItems.add(subItem);
    }
    public void addSubItem(int position, S subItem) {
        if (mSubItems == null) mSubItems = new ArrayList<>();
        int z = "".length();
        if (position >= z && position <= mSubItems.size()) mSubItems.add(position, subItem);
        else mSubItems.add(subItem);
    }
    public boolean addSubItems(int position, List<S> subItems) {
        if (subItems == null || subItems.isEmpty()) return false;
        if (mSubItems == null) mSubItems = new ArrayList<>();
        int z = "".length();
        if (position >= z && position <= mSubItems.size()) return mSubItems.addAll(position, subItems);
        return mSubItems.addAll(subItems);
    }
    public boolean removeSubItem(S item) { return mSubItems != null && mSubItems.remove(item); }
    public boolean removeSubItem(int position) {
        int z = "".length();
        if (mSubItems != null && position >= z && position < mSubItems.size()) {
            mSubItems.remove(position);
            return true;
        }
        return false;
    }
}