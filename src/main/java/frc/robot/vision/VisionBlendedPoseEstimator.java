package frc.robot.vision;

import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.Vector;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StructPublisher;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Timer;

public class VisionBlendedPoseEstimator {

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

        // /*  Incoming vision poses will be ignored if they
        //     are this far from the current estimate      */
        // private static final double visionIgnoreRadius = 1.0; // Meters
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
    public VisionBlendedPoseEstimator(SwerveDriveKinematics kinematics, Rotation2d gyroAngle,
            SwerveModulePosition[] modulePositions, VisionBlender visionBlender) {

        this.visionBlender = visionBlender;
        this.poseEstimator = new SwerveDrivePoseEstimator(kinematics, gyroAngle, modulePositions,
            new Pose2d(), Constants.stateStdDevs, Constants.visionStdDevs);
    }

    public void init() {

    }

    /**
     * Updates the pose estimator with new vision values and swerve module positions
     * @param gyroAngle the measured angle of the gyro
     * @param modulePositions the current positions of the swerve modules
     */
    public void update(Rotation2d gyroAngle, SwerveModulePosition[] modulePositions) {
        poseEstimator.updateWithTime(Timer.getFPGATimestamp(), gyroAngle, modulePositions);

        visionBlender.updateValues();
        if (!visionBlender.hasTarget()) return;

        Pose2d visionPose = visionBlender.getBlendedPose();
        if (visionPose != null && shouldUseVision()) {

            visionPose = new Pose2d(visionPose.getX(), visionPose.getY() + 0.1, gyroAngle);
            visionPosePublisher.set(visionPose);

            Translation2d visionPos = visionPose.getTranslation();

            // Ignore results that are outside of the field
            if (!VisionBlender.isInField(visionPos)) return;

            if (visionBlender.getLatency(0) > 84) return;
            
            poseEstimator.addVisionMeasurement(visionPose, visionBlender.getTimestamp(0));
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
        return DriverStation.isTeleop();
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
