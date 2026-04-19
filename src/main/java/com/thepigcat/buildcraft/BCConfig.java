package com.thepigcat.buildcraft;

import com.portingdeadmods.portingdeadlibs.api.config.ConfigValue;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;

public final class BCConfig {
    @ConfigValue(name = "Tank Capacity", comment = "The maximum amount of fluid a tank can store", category = "capacity.fluid")
    public static int tankCapacity = 8000;

    @ConfigValue(name = "Combustion Engine Fluid Capacity", comment = "The maximum amount of fluid a combustion engine can store", category = "capacity.fluid")
    public static int combustionEngineFluidCapacity = 2000;

    @ConfigValue(name = "Tank Retain Fluids", comment = "Whether the Fluid Tank retains its contents after being broken")
    public static boolean tankRetainFluids = true;
    @ConfigValue(name = "Create Retain Items", comment = "Whether the Crate retains its contents after being broken")
    public static boolean crateRetainItems = true;

    @ConfigValue(name = "Redstone Engine Energy Capacity", comment = "The maximum amount of energy a redstone engine can store", category = "capacity.energy")
    public static int redstoneEngineEnergyCapacity = 1000;
    @ConfigValue(name = "Stirling Engine Energy Capacity", comment = "The maximum amount of energy a stirling engine can store", category = "capacity.energy")
    public static int stirlingEngineEnergyCapacity = 5000;
    @ConfigValue(name = "Combustion Engine Energy Capacity", comment = "The maximum amount of energy a combustion engine can store", category = "capacity.energy")
    public static int combustionEngineEnergyCapacity = 10_000;

    @ConfigValue(name = "Redstone Engine Energy Production", comment = "The amount of energy a redstone engine produces", category = "production.energy")
    public static int redstoneEngineEnergyProduction = 5;
    @ConfigValue(name = "Stirling Engine Energy Production", comment = "The amount of energy a stirling engine produces per cycle", category = "production.energy")
    public static int stirlingEngineEnergyProduction = 20;
    @ConfigValue(name = "Combustion Engine Energy Production", comment = "The amount of energy a combustion engine produces per cycle", category = "production.energy")
    public static int combustionEngineEnergyProduction = 40;

    @ConfigValue(name = "Crate Item Capacity", comment = "The maximum amount of items the crate can store", category = "capacity.items")
    public static int crateItemCapacity = 4096;

    @ConfigValue(name = "Extraction Pipe Energy Cost", comment = "FE consumed per item extraction by wooden/emerald pipes", category = "production.energy")
    public static int extractionEnergyCost = 10;

    @ConfigValue(name = "Extraction Pipe Energy Capacity", comment = "Internal energy buffer of extraction pipes", category = "capacity.energy")
    public static int extractionPipeEnergyCapacity = 1000;

    @ConfigValue(name = "Fluid Extraction Rate", comment = "Millibuckets of fluid extracted per FE consumed by wooden fluid pipe (like original BC: engine power determines extraction speed)", category = "production.fluid")
    public static int fluidExtractionRate = 5;

    @ConfigValue(name = "Kinesis Pipe Energy Capacity", comment = "Base energy buffer of kinesis (power) pipes", category = "capacity.energy")
    public static int kinesisPipeEnergyCapacity = 1000;

    @ConfigValue(name = "Quarry Energy Capacity", category = "capacity.energy")
    public static int quarryEnergyCapacity = 50_000;

    @ConfigValue(name = "Quarry Energy Per Block", comment = "FE consumed per block mined", category = "production.energy")
    public static int quarryEnergyPerBlock = 20;

    // Assembly Table / Laser
    @ConfigValue(name = "Laser Battery Capacity", comment = "FE capacity of the Laser block", category = "capacity.energy")
    public static int laserBatteryCapacity = 4000;
    @ConfigValue(name = "Laser Max Receive", comment = "Max FE/t the Laser accepts from kinesis pipes", category = "capacity.energy")
    public static int laserMaxReceive = 200;
    @ConfigValue(name = "Laser Max Output", comment = "Max FE/t the Laser pushes to Assembly Table per tick", category = "production.energy")
    public static int laserMaxOutput = 40;

    // Chipset FE costs
    @ConfigValue(name = "Red Chipset FE Cost", comment = "FE required to craft a Red (Redstone) Chipset", category = "assembly")
    public static int redChipsetFeCost = 10_000;
    @ConfigValue(name = "Iron Chipset FE Cost", comment = "FE required to craft an Iron Chipset", category = "assembly")
    public static int ironChipsetFeCost = 20_000;
    @ConfigValue(name = "Gold Chipset FE Cost", comment = "FE required to craft a Gold Chipset", category = "assembly")
    public static int goldChipsetFeCost = 40_000;
    @ConfigValue(name = "Quartz Chipset FE Cost", comment = "FE required to craft a Quartz Chipset", category = "assembly")
    public static int quartzChipsetFeCost = 60_000;
    @ConfigValue(name = "Diamond Chipset FE Cost", comment = "FE required to craft a Diamond Chipset", category = "assembly")
    public static int diamondChipsetFeCost = 80_000;
}
