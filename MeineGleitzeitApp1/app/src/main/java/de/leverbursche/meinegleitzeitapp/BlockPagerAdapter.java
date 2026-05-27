package de.leverbursche.meinegleitzeitapp;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import java.util.ArrayList;
import java.util.List;

public class BlockPagerAdapter extends FragmentStateAdapter {

    static final int MAX_BLOCKS = 8;

    private final int jobIndex;
    // Stabile Block-Indizes (1-basiert, job-lokal). Item-IDs = (jobIndex-1)*100 + blockIndex.
    private final List<Integer> blockIndices = new ArrayList<>();
    private int nextBlockIndex;

    /**
     * @param activity       Die Host-Activity
     * @param jobIndex       1 oder 2
     * @param initialIndices Wiederhergestellte Block-Indizes (z.B. [1, 2, 3])
     */
    public BlockPagerAdapter(@NonNull FragmentActivity activity, int jobIndex,
                             @NonNull List<Integer> initialIndices) {
        super(activity);
        this.jobIndex = jobIndex;
        blockIndices.addAll(initialIndices);
        nextBlockIndex = blockIndices.isEmpty() ? 2
                : blockIndices.stream().max(Integer::compareTo).orElse(1) + 1;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return BlockFragment.newInstance(jobIndex, blockIndices.get(position));
    }

    @Override
    public int getItemCount() {
        return blockIndices.size();
    }

    /**
     * Stabile ID = (jobIndex-1)*100 + blockIndex.
     * Job 1, Block 1–8 → IDs 1–8; Job 2, Block 1–8 → IDs 101–108.
     * Verhindert Kollisionen beim Wechsel zwischen Jobs.
     */
    @Override
    public long getItemId(int position) {
        return (long)(jobIndex - 1) * 100 + blockIndices.get(position);
    }

    @Override
    public boolean containsItem(long itemId) {
        int blockIdx = (int)(itemId - (long)(jobIndex - 1) * 100);
        return blockIndices.contains(blockIdx);
    }

    // === Öffentliche Methoden ===

    /** Fügt einen neuen Block ans Ende hinzu. Gibt den neuen Block-Index zurück. */
    public int addBlock() {
        int newIndex = nextBlockIndex++;
        blockIndices.add(newIndex);
        notifyItemInserted(blockIndices.size() - 1);
        return newIndex;
    }

    /**
     * Entfernt den Block an der angegebenen Position.
     * Block 1 (Position 0) kann nicht entfernt werden.
     */
    public void removeBlock(int position) {
        if (position <= 0 || position >= blockIndices.size()) return;
        blockIndices.remove(position);
        notifyItemRemoved(position);
    }

    /** Gibt den Block-Index an der angegebenen Position zurück. */
    public int getBlockIndex(int position) {
        return blockIndices.get(position);
    }

    /** Gibt alle aktuellen Block-Indizes zurück (für Persistenz in SharedPreferences). */
    public List<Integer> getBlockIndices() {
        return new ArrayList<>(blockIndices);
    }

    public boolean canAddBlock() {
        return blockIndices.size() < MAX_BLOCKS;
    }
}
