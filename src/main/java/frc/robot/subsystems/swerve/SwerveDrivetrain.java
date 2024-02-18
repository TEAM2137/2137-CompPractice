package frc.robot.subsystems.swerve;

import com.ctre.phoenix6.configs.Pigeon2Configuration;
import com.ctre.phoenix6.hardware.Pigeon2;

import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.trajectory.Trajectory;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.math.trajectory.TrajectoryConfig;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StructArrayPublisher;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.RobotContainer;
import frc.robot.util.CanIDs;
import frc.robot.util.PID;

// Everything in this file will be done in the order front left, front right, back left, back right
public class SwerveDrivetrain extends SubsystemBase {

    public static class Constants {
        public static final int gyroID = 5;

        // public static final String canBusName = "rio";

        public static final double length = Units.inchesToMeters(21.5);
        public static final double width = Units.inchesToMeters(21.5);

        public static final double driveMaxSpeed = 3.0;
        public static final double driveMaxAccel = 1.0;

        public static SwerveModuleConstants frontLeft = new SwerveModuleConstants(
            CanIDs.get("fl-drive"), 
            CanIDs.get("fl-turn"), 
            CanIDs.get("fl-encoder"), 
            0, "Front Left");
        public static SwerveModuleConstants frontRight = new SwerveModuleConstants(
            CanIDs.get("fr-drive"), 
            CanIDs.get("fr-turn"), 
            CanIDs.get("fr-encoder"), 
            0, "Front Right");
        public static SwerveModuleConstants backLeft = new SwerveModuleConstants(
            CanIDs.get("bl-drive"), 
            CanIDs.get("bl-turn"), 
            CanIDs.get("bl-encoder"),
            0, "Back Left");
        public static SwerveModuleConstants backRight = new SwerveModuleConstants(
            CanIDs.get("br-drive"),
            CanIDs.get("br-turn"), 
            CanIDs.get("br-encoder"), 
            0, "Back Right");

        public static PID translationPIDConstants = new PID(0.5, 0, 0);

        public static PID teleopThetaPIDConstants = new PID(0.5, 0.0, 0.4);
        public static TrapezoidProfile.Constraints teleopThetaPIDConstraints = new TrapezoidProfile.Constraints(6, 4); // new

        public static PID autoThetaPIDConstants = new PID(2.5, 0, 0);
        public static TrapezoidProfile.Constraints autoThetaPIDConstraints = new TrapezoidProfile.Constraints(16, 16); // old

        public static PID purePIDTranslationConstants = new PID(0, 0, 0); // can be ignored

        public static class SwerveModuleConstants {
            public final int driveID;
            public final int turningID;
            public final int encoderID;

            public double offset;
            public final String moduleName;

            SwerveModuleConstants(int driveID, int turningID, int encoderID, double offsetDegrees, String moduleName) {
                this.driveID = driveID;
                this.turningID = turningID;
                this.encoderID = encoderID;
                this.offset = offsetDegrees;
                this.moduleName = moduleName;
            }
        }
    }

    SwerveDriveKinematics kinematics;

    private double[] offsets = new double[4];

    private SwerveModule frontLeftModule;
    private SwerveModule frontRightModule;
    private SwerveModule backLeftModule;
    private SwerveModule backRightModule;

    private SwerveModule[] swerveArray;
    private SwerveModulePosition[] modulePositions = new SwerveModulePosition[4];

    private Timer timer;
    private double lastTime;

    private Pigeon2 pigeonIMU;

    private SwerveDrivePoseEstimator poseEstimator;

    private Field2d field2d = new Field2d();

    private StructArrayPublisher<SwerveModuleState> swervePublisher = NetworkTableInstance.getDefault()
        .getStructArrayTopic("Swerve States", SwerveModuleState.struct).publish();

    /**
     * Creates a swerve drivetrain (uses values from constants)
     */
    public SwerveDrivetrain(ModuleType moduleType) {
        // locations of all of the modules (for kinematics)
        Translation2d frontLeftLocation = new Translation2d(Constants.length / 2, Constants.width / 2);
        Translation2d frontRightLocation = new Translation2d(Constants.length / 2, -Constants.width / 2);
        Translation2d backLeftLocation = new Translation2d(-Constants.length / 2, Constants.width / 2);
        Translation2d backRightLocation = new Translation2d(-Constants.length / 2, -Constants.width / 2);

        // the kinematics object for converting chassis speeds to module rotations and powers
        kinematics = new SwerveDriveKinematics(frontLeftLocation, frontRightLocation, backLeftLocation, backRightLocation);

        // each of the modules
        if(moduleType == ModuleType.Neo) {
            frontLeftModule = new NeoModule(Constants.frontLeft);
            frontRightModule = new NeoModule(Constants.frontRight);
            backLeftModule = new NeoModule(Constants.backLeft);
            backRightModule = new NeoModule(Constants.backRight);
        }else{
            frontLeftModule = new FalconModule(Constants.frontLeft);
            frontRightModule = new FalconModule(Constants.frontRight);
            backLeftModule = new FalconModule(Constants.backLeft);
            backRightModule = new FalconModule(Constants.backRight);
        }

        // an array of the swerve modules, to make life easier
        swerveArray = new SwerveModule[]{frontLeftModule, frontRightModule, backLeftModule, backRightModule};

        // the gyro
        pigeonIMU = new Pigeon2(Constants.gyroID, RobotContainer.getRioCanBusName());
        pigeonIMU.getConfigurator().apply(new Pigeon2Configuration());
        pigeonIMU.reset();

        // create pose estimator
        updateModulePositions();
        poseEstimator = new SwerveDrivePoseEstimator(kinematics, new Rotation2d(), modulePositions, new Pose2d());
        
        // new MatBuilder<>(Nat.N3(), Nat.N1()).fill(0.05, 0.05, Units.degreesToRadians(5)), // State measurement standard deviations. X, Y, theta.
        // new MatBuilder<>(Nat.N1(), Nat.N1()).fill(Units.degreesToRadians(0.01)), // Local measurement standard deviations. Gyro.
        // new MatBuilder<>(Nat.N3(), Nat.N1()).fill(0.5, 0.5, Units.degreesToRadians(30))); // Vision measurement standard deviations. X, Y, and theta.

//        SmartDashboard.putBoolean("Reset Position", false);

        timer = new Timer();
        timer.reset();
        timer.start();
        
        resetOdometry();
    }

    public void displayCurrentOffsets() {
        SmartDashboard.putNumber("Offset FL", swerveArray[0].encoderOffset);
        SmartDashboard.putNumber("Offset FR", swerveArray[1].encoderOffset);
        SmartDashboard.putNumber("Offset BL", swerveArray[2].encoderOffset);
        SmartDashboard.putNumber("Offset BR", swerveArray[3].encoderOffset);
    }

    public void saveOffsets() {
        SmartDashboard.putNumber("Offset FL", swerveArray[0].currentPosition);
        SmartDashboard.putNumber("Offset FR", swerveArray[1].currentPosition);
        SmartDashboard.putNumber("Offset BL", swerveArray[2].currentPosition);
        SmartDashboard.putNumber("Offset BR", swerveArray[3].currentPosition);
        loadOffsets();
    }

    public void loadOffsets() {
        offsets[0] = SmartDashboard.getNumber("Offset FL", 0);
        offsets[1] = SmartDashboard.getNumber("Offset FR", 0);
        offsets[2] = SmartDashboard.getNumber("Offset BL", 0);
        offsets[3] = SmartDashboard.getNumber("Offset BR", 0);
        swerveArray[0].encoderOffset = offsets[0];
        swerveArray[1].encoderOffset = offsets[1];
        swerveArray[2].encoderOffset = offsets[2];
        swerveArray[3].encoderOffset = offsets[3];
    }

    /**
     * Periodic loop of the subsystem
     */
    @Override
    public void periodic() {

        updateOdometry();

        field2d.setRobotPose(getPose());

        // SmartDashboard.putNumber("Pigeon Angle", getRobotAngle().getDegrees());
        SmartDashboard.putNumber("Drivetrain Angle", getPose().getRotation().getDegrees());
        SmartDashboard.putNumber("Drivetrain X", getPose().getX());
        SmartDashboard.putNumber("Drivetrain Y", getPose().getY());

//        if(SmartDashboard.getBoolean("Reset Position", false)) {
//            resetOdometry();
//            SmartDashboard.putBoolean("Reset Position", false);
//        }

//        for(SwerveDriveModule module : swerveArray) {
//            module.periodic();
//        }

        //SmartDashboard.putNumber("FusedHeading", getRobotAngle().getDegrees());
        SmartDashboard.putData("Field", field2d);

        swervePublisher.set(getSwerveModuleStates()); // AdvantageScope swerve states
    }

    private void updateOdometry() {
        double time = timer.get();
        double dt = time - lastTime;
        lastTime = time;

        if (dt == 0) {
            return;
        }

        double[] distances = new double[] {
            frontLeftModule.getDriveDistance(),
            frontRightModule.getDriveDistance(),
            backLeftModule.getDriveDistance(),
            backRightModule.getDriveDistance()
        };

        SmartDashboard.putNumber("FrontLeft-DriveDistance", distances[0]);
        SmartDashboard.putNumber("FrontRight-DriveDistance", distances[1]);
        SmartDashboard.putNumber("BackLeft-DriveDistance", distances[2]);
        SmartDashboard.putNumber("BackRight-DriveDistance", distances[3]);

        // modulePositions[0] = new SwerveModulePosition(-(distances[0] - lastDistances[0]) / dt, frontLeftModule.getModuleRotation());
        // modulePositions[1] = new SwerveModulePosition(-(distances[1] - lastDistances[1]) / dt, frontRightModule.getModuleRotation());
        // modulePositions[2] = new SwerveModulePosition(-(distances[2] - lastDistances[2]) / dt, backLeftModule.getModuleRotation());
        // modulePositions[3] = new SwerveModulePosition(-(distances[3] - lastDistances[3]) / dt, backRightModule.getModuleRotation());

        modulePositions[0] = new SwerveModulePosition(distances[0], frontLeftModule.getModuleRotation());
        modulePositions[1] = new SwerveModulePosition(distances[1], frontRightModule.getModuleRotation());
        modulePositions[2] = new SwerveModulePosition(distances[2], backLeftModule.getModuleRotation());
        modulePositions[3] = new SwerveModulePosition(distances[3], backRightModule.getModuleRotation());

        poseEstimator.updateWithTime(time, getRobotAngle(), modulePositions);
    }

    private void updateModulePositions() {
        double[] distances = new double[] {
            frontLeftModule.getDriveDistance(),
            frontRightModule.getDriveDistance(),
            backLeftModule.getDriveDistance(),
            backRightModule.getDriveDistance()
        };

        modulePositions[0] = new SwerveModulePosition(distances[0], frontLeftModule.getModuleRotation());
        modulePositions[1] = new SwerveModulePosition(distances[1], frontRightModule.getModuleRotation());
        modulePositions[2] = new SwerveModulePosition(distances[2], backLeftModule.getModuleRotation());
        modulePositions[3] = new SwerveModulePosition(distances[3], backRightModule.getModuleRotation());
    }

    public void resetModuleAngles() {
        for(SwerveModule module : swerveArray) {
            module.homeTurningMotor();
        }
    }

    /**
     * @return the angle of the robot (CCW positive (normal))
     */
    public Rotation2d getRobotAngle() {
        double raw = pigeonIMU.getYaw().getValueAsDouble(); // % 360;
//        while (raw <= -180) raw += 360;
//        while (raw > 180) raw -= 360;
        return Rotation2d.fromDegrees(raw);

//        if (raw < 0 && raw > -180) {
//            return Rotation2d.fromDegrees()
//        }
//        if( raw > 180) {
//            raw = raw - 360;
//            pigeonIMU.setFusedHeading(raw);
//        }else if( raw < -180) {
//            raw = raw + 360;
//            pigeonIMU.setFusedHeading(raw);
//        }
//        return Rotation2d.fromDegrees(raw);
//        if (value > 180)
//            return Rotation2d.fromDegrees(value - 360);
//        else
//            return Rotation2d.fromDegrees(value);
//        return Rotation2d.fromDegrees(Math.abs(pigeonIMU.getFusedHeading()) % 360);
    }

    public double getThetaVelocity() {
        //double[] tmp = new double[3];
        //pigeonIMU.getRawGyro(tmp);
        
        //return tmp[2];
        return pigeonIMU.getAngularVelocityZDevice().getValueAsDouble();
    }

    public void resetDriveDistances() {
        frontLeftModule.resetDriveEncoder();
        frontRightModule.resetDriveEncoder();
        backLeftModule.resetDriveEncoder();
        backRightModule.resetDriveEncoder();
    }

    public void resetGyro() {
        pigeonIMU.setYaw(0);
    }

    public void addVisionReading(Pose2d pose, double processingTime) {
        this.poseEstimator.addVisionMeasurement(pose, processingTime);
    }

    /**
     * @param speeds speed of the chassis with -1 to 1 on translation
     */
    public void driveTranslationRotationRaw(ChassisSpeeds speeds) {
        if(speeds.vxMetersPerSecond + speeds.vyMetersPerSecond + speeds.omegaRadiansPerSecond == 0) {
            // if power isn't being applied, don't set the module rotation to zero
            setAllModuleDriveRawPower(0);
            selfTargetAllModuleAngles();
        } else {
            // if power, drive it
            SwerveModuleState[] states = kinematics.toSwerveModuleStates(speeds); //convert speeds to individual modules

            SwerveDriveKinematics.desaturateWheelSpeeds(states, 1); //normalize speeds to be all between -1 and 1
            for (int i = 0; i < states.length; i++) {
                //optimize module rotation (instead of a >90 degree turn, turn less and flip wheel direction)
                states[i] = SwerveModuleState.optimize(states[i], swerveArray[i].getModuleRotation());

                //set all the things
                swerveArray[i].setTurningTarget(states[i].angle);
                swerveArray[i].setDrivePowerRaw(states[i].speedMetersPerSecond);
            }
        }
    }

    /**
     * @param speeds speed of the chassis in m/s and rad/s
     */
    public void driveTranslationRotationVelocity(ChassisSpeeds speeds) {
        if(speeds.vxMetersPerSecond + speeds.vyMetersPerSecond + speeds.omegaRadiansPerSecond == 0) {
            // if power isn't being applied, don't set the module rotation to zero
            for (int i = 0; i < swerveArray.length; i++) {
                swerveArray[i].setDriveVelocity(0);
                swerveArray[i].setTurningTarget(swerveArray[i].getModuleRotation());
            }
        } else {
            // if power, drive it
            SwerveModuleState[] states = kinematics.toSwerveModuleStates(speeds); //convert speeds to individual modules

            SwerveDriveKinematics.desaturateWheelSpeeds(states, Constants.driveMaxSpeed); //normalize speeds to be all between min and max speed
            for (int i = 0; i < states.length; i++) {
                //optimize module rotation (instead of a >90 degree turn, turn less and flip wheel direction)
                states[i] = SwerveModuleState.optimize(states[i], swerveArray[i].getModuleRotation());

                //set all the things
                swerveArray[i].setTurningTarget(states[i].angle);
                swerveArray[i].setDriveVelocity(states[i].speedMetersPerSecond);
            }
        }
    }

    /**
     * @return swerve module states in m/s
     */
    public SwerveModuleState[] getSwerveModuleStates() {
        SwerveModuleState[] states = new SwerveModuleState[4];
        for(int i = 0; i < swerveArray.length; i++) {
            states[i] = swerveArray[i].getSwerveModuleState();
        }
        return states;
    }

    /**
     * @return the pose of the robot in meters
     */
    public Pose2d getPose() {
        return poseEstimator.getEstimatedPosition();
    }

    /**
     * @param measurement Pose2d of the calculated position
     * @param timestamp the timestamp the measurement is from
     */
    public void addVisionMeasurement(Pose2d measurement, double timestamp) {
        poseEstimator.addVisionMeasurement(measurement, timestamp);
    }

    /**
     * For debugging
     * @param power power of the drive motors -1 to 1
     */
    public void setAllModuleDriveRawPower(double power) {
        frontLeftModule.setDrivePowerRaw(power);
        frontRightModule.setDrivePowerRaw(power);
        backLeftModule.setDrivePowerRaw(power);
        backRightModule.setDrivePowerRaw(power);
    }

    /**
     * For debugging
     * @param angle angle of all of the modules
     */
    public void setAllModuleRotations(Rotation2d angle) {
        frontLeftModule.setTurningTarget(angle);
        frontRightModule.setTurningTarget(angle);
        backLeftModule.setTurningTarget(angle);
        backRightModule.setTurningTarget(angle);
    }

    /**
     * For debugging
     * @param velocity target velocity in m/s
     */
    public void setAllModuleDriveVelocity(double velocity) {
        frontLeftModule.setDriveVelocity(velocity);
        frontRightModule.setDriveVelocity(velocity);
        backLeftModule.setDriveVelocity(velocity);
        backRightModule.setDriveVelocity(velocity);
    }

    public void selfTargetAllModuleAngles() {
        frontLeftModule.selfTargetAngle();
        frontRightModule.selfTargetAngle();
        backLeftModule.selfTargetAngle();
        backRightModule.selfTargetAngle();
    }

    public void resetOdometry(Pose2d pose) {
        updateOdometry();
        poseEstimator.resetPosition(getRobotAngle(), modulePositions, new Pose2d(pose.getX(), pose.getY(), pose.getRotation()));
    }

    public void resetOdometry(Translation2d translation) {
        resetOdometry(new Pose2d(translation, getRobotAngle()));
    }

    private void resetOdometry() {
        resetOdometry(new Pose2d());
    }

    /**
     * Sets the drivetrain into x-locking mode, making it defense resistant
     */
    public void xLock() {
        double length = Constants.length / 2;
        double width = Constants.width / 2;
        setAllModuleDriveRawPower(0);

        frontLeftModule.setTurningTarget(new Rotation2d(Math.atan2(width, length)));
        frontRightModule.setTurningTarget(new Rotation2d(Math.atan2(-width, length)));
        backLeftModule.setTurningTarget(new Rotation2d(Math.atan2(width, -length)));
        backRightModule.setTurningTarget(new Rotation2d(Math.atan2(-width, -length)));
    }

    public TrajectoryConfig getDefaultConstraint() {
        return new TrajectoryConfig(Constants.driveMaxSpeed, Constants.driveMaxAccel).setKinematics(kinematics);
//        return new TrajectoryConfig(Units.feetToMeters(13), Constants.driveMaxAccel).s
//        etKinematics(kinematics);
//        return new TrajectoryConfig(Units.feetToMeters(13), Constants.driveMaxAccel);
//        return new TrajectoryConfig(Constants.driveMaxSpeed, Constants.driveMaxAccel);
    }

    public void setDriveBrakeMode(boolean brake) {
        this.frontLeftModule.setDriveMode(brake);
        this.backLeftModule.setDriveMode(brake);
        this.frontRightModule.setDriveMode(brake);
        this.backRightModule.setDriveMode(brake);
    }

    public void setTurnBrakeMode(boolean brake) {
        this.frontLeftModule.setTurnBrakeMode(brake);
        this.frontRightModule.setTurnBrakeMode(brake);
        this.backLeftModule.setTurnBrakeMode(brake);
        this.backRightModule.setTurnBrakeMode(brake);
    }

    public void setField2dTrajectory(Trajectory trajectory) {
        field2d.getObject("path").setTrajectory(trajectory);
    }

    public ChassisSpeeds getSpeeds() {
        return kinematics.toChassisSpeeds(getSwerveModuleStates());
    }

    public enum ModuleType {
        Neo,
        Falcon
    }
}