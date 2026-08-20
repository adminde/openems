import { ControllerEssLimitTotalDischargeUtils } from "./limit-total-discharge.utils";

describe("ControllerEssLimitTotalDischargeUtils", () => {
    describe("deriveForceChargeSoc", () => {
        it("should keep the distance when the min soc is lowered", () => {
            expect(ControllerEssLimitTotalDischargeUtils.deriveForceChargeSoc(15, 10, 8)).toBe(3);
        });

        it("should keep the distance when the min soc is raised", () => {
            expect(ControllerEssLimitTotalDischargeUtils.deriveForceChargeSoc(15, 10, 90)).toBe(85);
        });

        it("should keep the force charge soc when the min soc is unchanged", () => {
            expect(ControllerEssLimitTotalDischargeUtils.deriveForceChargeSoc(15, 10, 15)).toBe(10);
        });

        it("should never return a negative force charge soc", () => {
            expect(ControllerEssLimitTotalDischargeUtils.deriveForceChargeSoc(15, 10, 3)).toBe(0);
        });

        it("should stay below the min soc when both values are equal", () => {
            expect(ControllerEssLimitTotalDischargeUtils.deriveForceChargeSoc(15, 15, 20)).toBe(19);
        });

        it("should stay below the min soc when the stored force charge soc is higher", () => {
            expect(ControllerEssLimitTotalDischargeUtils.deriveForceChargeSoc(10, 20, 30)).toBe(29);
        });
    });
});
