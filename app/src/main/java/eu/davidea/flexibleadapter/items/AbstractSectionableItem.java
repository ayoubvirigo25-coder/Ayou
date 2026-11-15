package eu.davidea.flexibleadapter.items;
import androidx.recyclerview.widget.RecyclerView;
public abstract class AbstractSectionableItem<VH extends RecyclerView.ViewHolder, H> extends AbstractFlexibleItem<VH> {
    protected H header;
    public AbstractSectionableItem(H header) { this.header = header; }
    public H getHeader() { return header; }
    public void setHeader(H header) { this.header = header; }
}