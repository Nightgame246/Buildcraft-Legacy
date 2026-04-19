package com.thepigcat.buildcraft.api.blockentities;

public interface ILaserTarget {
    /** FE still required to finish the active recipe. 0 if no active recipe. */
    int getRequiredLaserPower();
    /** Called by LaserBE each tick with the FE it is delivering. */
    void receiveLaserPower(int fe);
}
