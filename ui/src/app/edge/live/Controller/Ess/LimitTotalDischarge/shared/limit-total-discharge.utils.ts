export namespace ControllerEssLimitTotalDischargeUtils {

    /**
     * Derives the Force-Charge-SoC belonging to a changed Min-SoC.
     *
     * The distance between both values is preserved, so the Force-Charge-SoC
     * follows whenever the Min-SoC is raised or lowered. The Edge requires the
     * Force-Charge-SoC to stay below the Min-SoC, therefore the distance is at
     * least one percent. The result is never negative.
     *
     * @param minSoc The currently configured Min-SoC
     * @param forceChargeSoc The currently configured Force-Charge-SoC
     * @param newMinSoc The newly selected Min-SoC
     * @returns The Force-Charge-SoC matching the new Min-SoC
     */
    export function deriveForceChargeSoc(minSoc: number, forceChargeSoc: number, newMinSoc: number): number {
        const distance = Math.max(1, minSoc - forceChargeSoc);
        return Math.max(0, newMinSoc - distance);
    }
}
