package frc.robot;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import frc.robot.subsystems.SwerveDrivetrain;

public class RobotContainer {

    private final SwerveDrivetrain drivetrain = new SwerveDrivetrain();
    private final CommandXboxController driverController = new CommandXboxController(0);
    private final CommandXboxController operatorController = new CommandXboxController(1);

    public Command getAutonomousCommand() {
        return null;
    }

    private void configureBindings() {
        // Example usage of this method:
        // driverController.b().whileTrue(exampleCommand());
    }
}
