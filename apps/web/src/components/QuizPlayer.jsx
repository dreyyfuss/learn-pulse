import { useState, useEffect } from 'react';
import Icon from './Icon';
import courseService from '../services/courseService';
import { getErrorMessage } from '../utils/errorMessages';

export default function QuizPlayer({ quizId, onPassed }) {
  const [quiz, setQuiz]         = useState(null);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState('');

  // 'idle' | 'taking' | 'submitted'
  const [phase, setPhase]       = useState('idle');
  const [selected, setSelected] = useState({}); // { [questionId]: optionId }
  const [result, setResult]     = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [alreadyPassed, setAlreadyPassed] = useState(false);
  const [bestScore, setBestScore] = useState(null);

  useEffect(() => {
    setLoading(true);
    setError('');
    setPhase('idle');
    setSelected({});
    setResult(null);

    Promise.all([
      courseService.getQuizForPlayer(quizId),
      courseService.getBestAttempt(quizId).catch(() => ({ data: null })),
    ])
      .then(([quizRes, bestRes]) => {
        const quizData = quizRes.data ?? quizRes;
        setQuiz(quizData);
        const best = bestRes?.data ?? bestRes;
        if (best && best.passed) {
          setAlreadyPassed(true);
          setBestScore(best.score);
        } else if (best && best.score != null) {
          setBestScore(best.score);
        }
      })
      .catch(err => setError(getErrorMessage(err)))
      .finally(() => setLoading(false));
  }, [quizId]);

  const startQuiz = () => {
    setSelected({});
    setResult(null);
    setPhase('taking');
  };

  const submit = async () => {
    if (!quiz) return;
    setSubmitting(true);
    try {
      const res = await courseService.submitQuizAttempt(quizId, selected);
      const attempt = res.data ?? res;
      setResult(attempt);
      setPhase('submitted');
      if (attempt.passed) {
        setBestScore(attempt.score);
        setAlreadyPassed(true);
        onPassed?.({ nextModuleId: attempt.nextModuleId, courseCompleted: attempt.courseCompleted });
      } else {
        if (bestScore === null || attempt.score > bestScore) setBestScore(attempt.score);
      }
    } catch (err) {
      setResult(null);
      setPhase('idle');
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return (
    <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>Loading quiz…</div>
  );
  if (error) return (
    <div style={{ padding: 32, color: 'var(--danger)' }}>{error}</div>
  );
  if (!quiz) return null;

  const allAnswered = quiz.questions.every(q => selected[q.id] != null);

  return (
    <div style={{ maxWidth: 680, padding: '0 0 40px' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
        <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--ink-3)', background: 'var(--surface-2)', border: '1px solid var(--rule)', padding: '2px 8px', borderRadius: 4 }}>
          Quiz
        </span>
        <span style={{ fontSize: 12, color: 'var(--ink-4)' }}>Pass: {quiz.passingScore}%</span>
      </div>
      <h2 style={{ fontFamily: 'var(--font-display)', fontWeight: 500, fontSize: 24, letterSpacing: '-0.01em', margin: '0 0 8px' }}>
        {quiz.title}
      </h2>
      {quiz.description && (
        <p style={{ color: 'var(--ink-2)', fontSize: 14, marginBottom: 24 }}>{quiz.description}</p>
      )}

      {/* Prior pass banner */}
      {alreadyPassed && phase !== 'submitted' && (
        <div style={{ background: 'var(--success-bg, #f0fdf4)', border: '1px solid var(--success, #22c55e)', borderRadius: 10, padding: '12px 16px', marginBottom: 24, display: 'flex', alignItems: 'center', gap: 10 }}>
          <Icon name="check-circle" size={16} color="var(--success, #22c55e)" />
          <span style={{ fontSize: 14, color: 'var(--success, #16a34a)' }}>
            You've passed this quiz with {bestScore}%.
          </span>
        </div>
      )}

      {/* Idle state */}
      {phase === 'idle' && (
        <div>
          <p style={{ fontSize: 14, color: 'var(--ink-3)', marginBottom: 20 }}>
            {quiz.questions.length} question{quiz.questions.length !== 1 ? 's' : ''} · {quiz.passingScore}% to pass
          </p>
          <button className="btn btn-primary btn-sm" onClick={startQuiz}>
            {alreadyPassed ? 'Retake quiz' : 'Start quiz'}
          </button>
        </div>
      )}

      {/* Taking state */}
      {phase === 'taking' && (
        <div>
          {quiz.questions.map((q, qi) => (
            <div key={q.id} style={{ marginBottom: 24 }}>
              <p style={{ fontWeight: 600, fontSize: 15, marginBottom: 12 }}>
                {qi + 1}. {q.questionText}
              </p>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {q.options.map(opt => {
                  const picked = selected[q.id] === opt.id;
                  return (
                    <label
                      key={opt.id}
                      style={{
                        display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer',
                        padding: '10px 14px', borderRadius: 8,
                        border: `1px solid ${picked ? 'var(--primary)' : 'var(--rule)'}`,
                        background: picked ? 'var(--primary-bg, #eff6ff)' : 'var(--surface-2)',
                        fontSize: 14,
                      }}
                    >
                      <input
                        type="radio"
                        name={`q-${q.id}`}
                        value={opt.id}
                        checked={picked}
                        onChange={() => setSelected(prev => ({ ...prev, [q.id]: opt.id }))}
                        style={{ accentColor: 'var(--primary)', flexShrink: 0 }}
                      />
                      {opt.optionText}
                    </label>
                  );
                })}
              </div>
            </div>
          ))}

          <button
            className="btn btn-primary btn-sm"
            onClick={submit}
            disabled={!allAnswered || submitting}
          >
            {submitting ? 'Submitting…' : 'Submit quiz'}
          </button>
        </div>
      )}

      {/* Submitted state */}
      {phase === 'submitted' && result && (
        <div>
          {/* Score banner */}
          <div style={{
            borderRadius: 12, padding: '20px 24px', marginBottom: 28,
            background: result.passed ? 'var(--success-bg, #f0fdf4)' : 'var(--danger-bg, #fff1f2)',
            border: `1px solid ${result.passed ? 'var(--success, #22c55e)' : 'var(--danger, #ef4444)'}`,
            display: 'flex', alignItems: 'center', gap: 14,
          }}>
            <Icon
              name={result.passed ? 'check-circle' : 'x-circle'}
              size={28}
              color={result.passed ? 'var(--success, #22c55e)' : 'var(--danger, #ef4444)'}
            />
            <div>
              <div style={{ fontWeight: 700, fontSize: 20 }}>
                {result.score}%
              </div>
              <div style={{ fontSize: 14, color: 'var(--ink-2)', marginTop: 2 }}>
                {result.passed
                  ? `Passed! (required ${result.passingScore}%)`
                  : `Not passed. Required ${result.passingScore}% — try again.`}
              </div>
            </div>
          </div>

          {/* Per-question feedback */}
          {quiz.questions.map((q, qi) => {
            const detail = result.questions?.find(r => r.questionId === q.id);
            const isCorrect = detail?.correct;
            return (
              <div
                key={q.id}
                style={{
                  marginBottom: 16, padding: '12px 16px', borderRadius: 8,
                  border: `1px solid ${isCorrect ? 'var(--success, #22c55e)' : 'var(--danger, #ef4444)'}`,
                  background: isCorrect ? 'var(--success-bg, #f0fdf4)' : 'var(--danger-bg, #fff1f2)',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8, marginBottom: 8 }}>
                  <Icon
                    name={isCorrect ? 'check' : 'x'}
                    size={14}
                    color={isCorrect ? 'var(--success, #22c55e)' : 'var(--danger, #ef4444)'}
                    style={{ marginTop: 2, flexShrink: 0 }}
                  />
                  <span style={{ fontSize: 14, fontWeight: 500 }}>{qi + 1}. {q.questionText}</span>
                </div>
                {q.options.map(opt => {
                  const wasSelected = detail?.selectedOptionId === opt.id;
                  const isCorrectOpt = detail?.correctOptionId === opt.id;
                  return (
                    <div
                      key={opt.id}
                      style={{
                        fontSize: 13, padding: '4px 8px', borderRadius: 4, marginBottom: 3,
                        background: isCorrectOpt ? 'rgba(34,197,94,0.12)' : wasSelected && !isCorrectOpt ? 'rgba(239,68,68,0.1)' : 'transparent',
                        fontWeight: isCorrectOpt ? 600 : 400,
                        color: isCorrectOpt ? 'var(--success, #16a34a)' : wasSelected && !isCorrectOpt ? 'var(--danger, #ef4444)' : 'var(--ink-2)',
                      }}
                    >
                      {isCorrectOpt ? '✓ ' : wasSelected && !isCorrectOpt ? '✗ ' : '  '}
                      {opt.optionText}
                    </div>
                  );
                })}
              </div>
            );
          })}

          {!result.passed && (
            <button className="btn btn-primary btn-sm" onClick={startQuiz} style={{ marginTop: 8 }}>
              Try again
            </button>
          )}
        </div>
      )}
    </div>
  );
}
