package com.thepigcat.buildcraft.content.items.blocks;

import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.content.blockentities.TankBE;
import com.thepigcat.buildcraft.data.BCDataComponents;
import com.thepigcat.buildcraft.registries.BCBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TankBlockItem extends BlockItem {
    public TankBlockItem(Properties properties) {
        super(BCBlocks.TANK.get(), properties);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TankBE tankBE) {
            // fill() — not setFluid() — so the item's fluid is ADDED to whatever
            // total onPlace already established for this stack. getFluidHandler()
            // returns the master's handler when this tank is a non-master member
            // of a merged stack, and setFluid would destructively overwrite the
            // stack's running total with just this item's slice (visible as
            // "all three tanks suddenly full" when placing a bridge between two
            // half-full tanks). fill() also caps at capacity, which defangs
            // oversized TANK_CONTENT values that older saveToItem paths might
            // have stamped onto the item.
            FluidStack itemFluid = stack.getOrDefault(BCDataComponents.TANK_CONTENT, SimpleFluidContent.EMPTY).copy();
            if (!itemFluid.isEmpty()) {
                tankBE.getFluidHandler().fill(itemFluid, IFluidHandler.FluidAction.EXECUTE);
            }
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        if (BCConfig.tankRetainFluids) {
            FluidStack stack1 = stack.getOrDefault(BCDataComponents.TANK_CONTENT, SimpleFluidContent.EMPTY).copy();
            if (!stack1.isEmpty()) {
                tooltipComponents.add(stack1.getHoverName().copy().append(", %d mb".formatted(stack1.getAmount())).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
