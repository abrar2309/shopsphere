
import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
    requestPasswordReset,
    confirmPasswordReset,
} from '../api/passwordResetApi';

function ForgotPassword() {
    const [step, setStep] = useState(1);
    const [email, setEmail] = useState('');
    const [otp, setOtp] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');

    async function handleRequestOtp(event) {
        event.preventDefault();
        setError('');
        setMessage('');

        try {
            setLoading(true);

            const response = await requestPasswordReset(email.trim());

            setMessage(
                response?.message ||
                'If an account exists for this email, password-reset instructions will be sent.'
            );

            setStep(2);
        } catch (err) {
            setError(
                err.response?.data?.message ||
                'Unable to process your request. Please try again.'
            );
        } finally {
            setLoading(false);
        }
    }

    async function handleConfirmReset(event) {
        event.preventDefault();
        setError('');
        setMessage('');

        if (!/^\d{6}$/.test(otp)) {
            setError('Enter the six-digit OTP.');
            return;
        }

        if (newPassword.length < 10 || newPassword.length > 100) {
            setError('Your new password must contain 10–100 characters.');
            return;
        }

        if (newPassword !== confirmPassword) {
            setError('The passwords do not match.');
            return;
        }

        try {
            setLoading(true);

            const response = await confirmPasswordReset(
                email.trim(),
                otp,
                newPassword
            );

            setMessage(
                response?.message ||
                'Your password has been reset successfully.'
            );

            setStep(3);
        } catch (err) {
            setError(
                err.response?.data?.message ||
                'Invalid or expired OTP. Please try again.'
            );
        } finally {
            setLoading(false);
        }
    }

    return (
        <main className="auth-page">
            <section className="auth-card">
                <span className="eyebrow">SHOPSPHERE ACCOUNT</span>

                {step === 1 && (
                    <>
                        <h1>Forgot password?</h1>
                        <p className="auth-description">
                            Enter your registered email address. We will send
                            you a password-reset OTP if an account exists.
                        </p>

                        <form onSubmit={handleRequestOtp}>
                            <label>
                                Email address
                                <input
                                    type="email"
                                    value={email}
                                    onChange={(event) => setEmail(event.target.value)}
                                    placeholder="you@example.com"
                                    required
                                    autoComplete="email"
                                />
                            </label>

                            {error && <p className="form-error" role="alert">{error}</p>}
                            {message && <p role="status">{message}</p>}

                            <button className="auth-submit" type="submit" disabled={loading}>
                                {loading ? 'Sending...' : 'Send OTP'}
                            </button>
                        </form>
                    </>
                )}

                {step === 2 && (
                    <>
                        <h1>Reset your password</h1>
                        <p className="auth-description">
                            Enter the six-digit OTP sent to your registered email
                            address and choose a new password.
                        </p>

                        <form onSubmit={handleConfirmReset}>
                            <label>
                                Email address
                                <input type="email" value={email} readOnly />
                            </label>

                            <label>
                                Six-digit OTP
                                <input
                                    type="text"
                                    inputMode="numeric"
                                    maxLength={6}
                                    value={otp}
                                    onChange={(event) =>
                                        setOtp(event.target.value.replace(/\D/g, ''))
                                    }
                                    placeholder="123456"
                                    required
                                    autoComplete="one-time-code"
                                />
                            </label>

                            <label>
                                New password
                                <input
                                    type="password"
                                    value={newPassword}
                                    onChange={(event) => setNewPassword(event.target.value)}
                                    minLength={10}
                                    maxLength={100}
                                    required
                                    autoComplete="new-password"
                                />
                            </label>

                            <label>
                                Confirm new password
                                <input
                                    type="password"
                                    value={confirmPassword}
                                    onChange={(event) => setConfirmPassword(event.target.value)}
                                    minLength={10}
                                    maxLength={100}
                                    required
                                    autoComplete="new-password"
                                />
                            </label>

                            {error && <p className="form-error" role="alert">{error}</p>}

                            <button className="auth-submit" type="submit" disabled={loading}>
                                {loading ? 'Resetting...' : 'Reset password'}
                            </button>
                        </form>

                        <p className="auth-switch">
                            Didn't receive an OTP?{' '}
                            <button type="button" onClick={handleRequestOtp} disabled={loading}>
                                Request another
                            </button>
                        </p>
                    </>
                )}

                {step === 3 && (
                    <>
                        <h1>Password updated!</h1>
                        <p className="auth-description" role="status">{message}</p>
                        <Link className="auth-submit" to="/login">
                            Continue to login
                        </Link>
                    </>
                )}

                {step !== 3 && (
                    <p className="auth-switch">
                        Remember your password? <Link to="/login">Back to login</Link>
                    </p>
                )}
            </section>
        </main>
    );
}

export default ForgotPassword;
