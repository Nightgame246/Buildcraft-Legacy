package com.thepigcat.buildcraft.util;

import com.thepigcat.buildcraft.api.capabilties.JumboItemHandler.BigStack;
import com.thepigcat.buildcraft.data.BCDataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ItemUtils {
    public static int hashStackList(List<BigStack> list) {
        int i = 0;

        for(BigStack bigStack : list) {
            i = i * 31 + ItemStack.hashItemAndComponents(bigStack.getSlotStack());
        }

        return i;
    }

    public static boolean listMatches(List<BigStack> list, List<BigStack> other) {
        if (list.size() != other.size()) {
            return false;
        } else {
            for(int i = 0; i < list.size(); ++i) {
                if (!ItemStack.matches(list.get(i).getSlotStack(), other.get(i).getSlotStack())) {
                    return false;
                }
            }

            return true;
        }
    }

    public static void setItemColor(ItemStack stack, DyeColor color) {
        stack.set(BCDataComponents.ITEM_COLOUR.get(), color);
    }

    public static @Nullable DyeColor getItemColor(ItemStack stack) {
        return stack.get(BCDataComponents.ITEM_COLOUR.get());
    }

    public static DyeColor getItemColorOrDefault(ItemStack stack, DyeColor defaultColor) {
        return stack.getOrDefault(BCDataComponents.ITEM_COLOUR.get(), defaultColor);
    }

    public static boolean hasItemColor(ItemStack stack, DyeColor color) {
        return color.equals(getItemColor(stack));
    }

}
