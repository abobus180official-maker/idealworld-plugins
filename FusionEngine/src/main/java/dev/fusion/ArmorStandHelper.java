package dev.fusion;

import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.EulerAngle;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

/**
 * Хелпер для поиска стойки, на которую смотрит игрок, и чтения её данных.
 */
public final class ArmorStandHelper {

	private ArmorStandHelper() {}

	/** Ищет стойку в направлении взгляда игрока (луч до 20 блоков). */
	public static ArmorStandEntity findTarget(net.minecraft.entity.player.PlayerEntity player) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0F);
		Vec3d end = eye.add(look.multiply(20.0));
		double reach = 20.0;

		ArmorStandEntity best = null;
		double bestDist = reach;
		List<ArmorStandEntity> stands = player.getWorld().getEntitiesByClass(
				ArmorStandEntity.class, player.getBoundingBox().expand(20.0), e -> true);
		for (ArmorStandEntity s : stands) {
			var box = s.getBoundingBox();
			if (box == null) continue;
			var intersected = box.raycast(eye, end).orElse(null);
			if (intersected != null) {
				double d = eye.squaredDistanceTo(intersected);
				if (d < bestDist) { bestDist = d; best = s; }
			}
		}
		return best;
	}

	/** Читает позы стойки как массив [x,y,z] x 6 частей (в градусах). */
	public static float[][] readPoses(ArmorStandEntity s) {
		return new float[][] {
				toDeg(s.getHeadRotation()), toDeg(s.getBodyRotation()),
				toDeg(s.getLeftArmRotation()), toDeg(s.getRightArmRotation()),
				toDeg(s.getLeftLegRotation()), toDeg(s.getRightLegRotation())
		};
	}

	/** Имена 6 частей в том же порядке, что и readPoses. */
	public static final String[] PART_NAMES = {"Head", "Body", "LeftArm", "RightArm", "LeftLeg", "RightLeg"};

	public static EulerAngle fromDeg(float[] v) {
		return new EulerAngle((float) Math.toRadians(v[0]), (float) Math.toRadians(v[1]), (float) Math.toRadians(v[2]));
	}

	private static float[] toDeg(EulerAngle a) {
		return new float[] { (float) Math.toDegrees(a.getPitch()), (float) Math.toDegrees(a.getYaw()), (float) Math.toDegrees(a.getRoll()) };
	}
}