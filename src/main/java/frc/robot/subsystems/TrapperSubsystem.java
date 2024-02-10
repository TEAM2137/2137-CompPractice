package frc.robot.subsystems;

import com.revrobotics.CANSparkBase;
import com.revrobotics.CANSparkMax;
import com.revrobotics.SparkAbsoluteEncoder;
import com.revrobotics.SparkPIDController;
import com.revrobotics.CANSparkLowLevel.MotorType;
import com.revrobotics.SparkAbsoluteEncoder.Type;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.util.CanIDs;

public class TrapperSubsystem extends SubsystemBase {
    private CANSparkMax trapperMotor;

    private CANSparkMax pivotMotor;
    private SparkAbsoluteEncoder pivotEncoder;
    private SparkPIDController pivotPID;

    private CANSparkMax armMotor;
    private SparkAbsoluteEncoder armEncoder;
    private SparkPIDController armPID;

    public TrapperSubsystem() {
        super();
        trapperMotor = new CANSparkMax(CanIDs.get("trapper-motor"), MotorType.kBrushless);

        pivotMotor = new CANSparkMax(CanIDs.get("trapper-pivot"), MotorType.kBrushless);
        pivotEncoder = pivotMotor.getAbsoluteEncoder(Type.kDutyCycle);
        pivotPID = pivotMotor.getPIDController();
        pivotPID.setFeedbackDevice(pivotEncoder);

        armMotor = new CANSparkMax(CanIDs.get("trapper-arm"), MotorType.kBrushless);
        armEncoder = armMotor.getAbsoluteEncoder(Type.kDutyCycle);
        armPID = armMotor.getPIDController();
        armPID.setFeedbackDevice(armEncoder);
    }

    public Command runMotor() {
        return runOnce(() -> trapperMotor.set(.5));
    }

    public Command stopMotor() {
        return runOnce(() -> trapperMotor.stopMotor());
    }

    public Command setPivotTarget(double target) {
        return runOnce(() -> pivotPID.setReference(target / 360, CANSparkBase.ControlType.kPosition));
    }

    public Command setArmTarget(double target) {
        return runOnce(() -> armPID.setReference(target / 360, CANSparkBase.ControlType.kPosition));
    }

    @Override
    public void periodic() {
        super.periodic();
        SmartDashboard.putNumber("Trapper Pivot Encoder Position", pivotEncoder.getPosition());
        SmartDashboard.putNumber("Trapper Arm Encoder Position", armEncoder.getPosition());
        SmartDashboard.updateValues();
    }
}
