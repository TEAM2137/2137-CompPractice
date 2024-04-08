package frc.robot.vision;

import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.Vector;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StructPublisher;
import edu.wpi.first.wpilibj.Timer;
import frc.robot.vision.VisionBlender.VisionReading;

public class VisionPoseEstimator {

    public static class Constants {
        /*  Standard deviation for the module states pose.
            Increase these values to put less trust in the pose */
        private static final Vector<N3> stateStdDevs = VecBuilder.fill(
            0.05, // Meters
            0.05, // Meters
            Units.degreesToRadians(5) // Radians
        );

        /*  Standard deviation for the limelight(s) pose.
            Increase these values to put less trust in the pose */
        private static final Vector<N3> visionStdDevs = VecBuilder.fill(
            0.8, // Meters
            0.8, // Meters
            Units.degreesToRadians(20) // Radians
        );
    }

    public SwerveDrivePoseEstimator poseEstimator;
    public VisionBlender visionBlender;

    private StructPublisher<Pose2d> visionPosePublisher = NetworkTableInstance.getDefault()
        .getStructTopic("Vision Pose", Pose2d.struct).publish();

    /**
     * Creates a new vision-blended swerve pose estimator
     * @param kinematics kinematics of the swerve drivetrain
     * @param gyroAngle the current angle of the gyro
     * @param modulePositions the current positions of the modules
     * @param visionBlender the vision blender to use for AprilTag data
     */
    public VisionPoseEstimator(SwerveDriveKinematics kinematics, Rotation2d gyroAngle,
            SwerveModulePosition[] modulePositions, VisionBlender visionBlender) {

        this.visionBlender = visionBlender;
        this.poseEstimator = new SwerveDrivePoseEstimator(kinematics, gyroAngle, modulePositions,
            new Pose2d(), Constants.stateStdDevs, Constants.visionStdDevs);
    }

    /**
     * Updates the pose estimator with valid vision values and the current swerve module positions
     * @param gyroAngle the measured angle of the gyro
     * @param modulePositions the current positions of the swerve modules
     */
    public void update(Rotation2d gyroAngle, SwerveModulePosition[] modulePositions) {
        poseEstimator.updateWithTime(Timer.getFPGATimestamp(), gyroAngle, modulePositions);

        if (!shouldUseVision()) return;

        visionBlender.updateValues();
        if (!visionBlender.hasTarget()) return;

        for (VisionReading reading : visionBlender.getReadings()) {
            Pose2d visionPose = new Pose2d(reading.getX(), reading.getY() + 0.1, gyroAngle);

            // Ignore invalid vision readings
            if (!reading.isInField() || !reading.isRecent()) continue;

            visionPosePublisher.set(visionPose);
            poseEstimator.addVisionMeasurement(visionPose, reading.getTimestamp());
        }
    }

    /**
     * @return the current estimated position
     */
    public Pose2d grabEstimatedPose() {
        return poseEstimator.getEstimatedPosition();
    }

    /**
     * @return true if the vision pose should be blended with the states
     */
    public boolean shouldUseVision() {
        // Change this if we ever need to disable vision for certain situations
        // return DriverStation.isTeleop();
        return true;
    }

    /**
     * @param rotation the gyro rotation to reset to
     * @param pose2d the pose of the robot to reset to
     * @param modulePositions the current positions of the modules
     */
    public void resetPosition(Rotation2d rotation, Pose2d pose2d, SwerveModulePosition[] modulePositions) {
        poseEstimator.resetPosition(rotation, modulePositions, pose2d);
    }
}
