package frc.robot;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.commands.PathPlannerAuto;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import frc.robot.subsystems.swerve.SwerveDrivetrain;
import frc.robot.vision.AprilTagVision;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class RobotContainer {
    private final CommandXboxController driverController = new CommandXboxController(0);
    private final CommandXboxController operatorController = new CommandXboxController(1);

    private final SwerveDrivetrain driveSubsystem = new SwerveDrivetrain();
    public static final boolean disableSwerve = false;

    private final AprilTagVision vision = new AprilTagVision();

    private final SendableChooser<Command> autoChooser;

    private boolean moveForward;
    private boolean moveRight;

    private double rightStickRot;

    public RobotContainer() {
        configureBindings();

        autoChooser = AutoBuilder.buildAutoChooser();
        SmartDashboard.putData("Auto Chooser", autoChooser);
        SmartDashboard.putBoolean("Save Offsets", false);
        //driveSubsystem.displayCurrentOffsets();
        //driveSubsystem.loadOffsets();
    }

    public void runTeleop() {
        if (disableSwerve) return;
        driverController.a().onTrue(Commands.runOnce(() -> moveForward = true));
        driverController.a().onFalse(Commands.runOnce(() -> moveForward = false));
        driverController.b().onTrue(Commands.runOnce(() -> moveRight = true));
        driverController.b().onFalse(Commands.runOnce(() -> moveRight = false));
        
        driveSubsystem.setDefaultCommand(
            new RunCommand(
                () -> {
                    double direction = driveSubsystem.getRobotAngle().getRadians();

                    double controllerX = -driverController.getLeftX();
                    double controllerY = -driverController.getLeftY();

                    double rotationX = -driverController.getRightX();
                    double rotationY = -driverController.getRightY();
                    double rotation = Math.toDegrees(Math.atan2(rotationY, rotationX));

                    rightStickRot = rotation;
                    SmartDashboard.putNumber("Right Stick Rotation", rightStickRot);

                    double speedX = Math.sin(direction) * -controllerY + Math.cos(direction) * controllerX;
                    double speedY = Math.cos(direction) * controllerY + Math.sin(direction) * controllerX;
                    double rot = -driverController.getRightX();

                    // Controller deadzones
                    speedX = Math.abs(speedX) < 0.3 ? 0 : speedX;
                    speedY = Math.abs(speedY) < 0.3 ? 0 : speedY;
                    rot = Math.abs(rot) < 0.3 ? 0 : rot;
                    
                    driveSubsystem.driveTranslationRotationRaw(
                        new ChassisSpeeds(
                            moveForward ? 0.5 : speedY * 0.5,
                            moveRight ? 0.5 : speedX * 0.5,
                            rot
                        )
                    );
                },
                driveSubsystem
        ));
    }

    public Command getAutonomousCommand() {
        return new PathPlannerAuto("Test Auto");
    }

    private void configureBindings() {
        // Example usage of this method:
        // driverController.b().whileTrue(exampleCommand());
    }

    public void autonomousInit() {
        driveSubsystem.resetGyro();
        driveSubsystem.resetDriveDistances();
    }
}
