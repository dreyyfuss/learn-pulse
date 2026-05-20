import { useState, useEffect } from 'react';
import Icon from './Icon';
import courseService from '../services/courseService';
import { getErrorMessage } from '../utils/errorMessages';

export default function QuizEditor({ courseId, moduleId, quiz, isLocked, onUpdated, onDeleted }) {
  const [titleVal, setTitleVal]         = useState(quiz.title ?? '');
  const [descVal, setDescVal]           = useState(quiz.description ?? '');
  const [passingScore, setPassingScore] = useState(quiz.passingScore ?? 70);
  const [questions, setQuestions]       = useState([]);
  const [saving, setSaving]             = useState(false);
  const [savingQ, setSavingQ]           = useState(false);
  const [toast, setToast]               = useState('');

  const showToast = (msg) => { setToast(msg); setTimeout(() => setToast(''), 3000); };

  // Load full quiz detail (with questions) when quiz changes
  useEffect(() => {
    setTitleVal(quiz.title ?? '');
    setDescVal(quiz.description ?? '');
    setPassingScore(quiz.passingScore ?? 70);
    courseService.getQuizForInstructor(courseId, moduleId, quiz.id)
      .then(detail => {
        setQuestions((detail.data ?? detail).questions ?? []);
      })
      .catch(() => setQuestions([]));
  }, [quiz.id]);

  const saveQuiz = async () => {
    if (!titleVal.trim()) return;
    setSaving(true);
    try {
      const updated = await courseService.updateQuiz(courseId, moduleId, quiz.id, {
        title: titleVal.trim(),
        description: descVal || null,
        passingScore: Number(passingScore),
      });
      onUpdated(updated.data ?? updated);
      showToast('Quiz saved.');
    } catch (err) {
      showToast(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const deleteQuiz = async () => {
    try {
      await courseService.deleteQuiz(courseId, moduleId, quiz.id);
      onDeleted();
    } catch (err) {
      showToast(getErrorMessage(err));
    }
  };

  const saveQuestions = async () => {
    // Validate each question has exactly one correct option
    for (const q of questions) {
      const correctCount = q.options.filter(o => o.isCorrect).length;
      if (correctCount !== 1) {
        showToast(`"${q.questionText || 'A question'}" must have exactly one correct answer.`);
        return;
      }
      if (q.options.some(o => !o.optionText.trim())) {
        showToast('All option fields must be filled in.');
        return;
      }
    }
    setSavingQ(true);
    try {
      const payload = {
        questions: questions.map(q => ({
          questionText: q.questionText,
          questionType: q.questionType,
          options: q.options.map(o => ({ optionText: o.optionText, isCorrect: o.isCorrect })),
        })),
      };
      const updated = await courseService.upsertQuizQuestions(courseId, moduleId, quiz.id, payload);
      setQuestions((updated.data ?? updated).questions ?? []);
      showToast('Questions saved.');
    } catch (err) {
      showToast(getErrorMessage(err));
    } finally {
      setSavingQ(false);
    }
  };

  const addQuestion = (type) => {
    const defaultOptions = type === 'TRUE_FALSE'
      ? [{ optionText: 'True', isCorrect: true, orderIndex: 0 }, { optionText: 'False', isCorrect: false, orderIndex: 1 }]
      : [{ optionText: '', isCorrect: true, orderIndex: 0 }, { optionText: '', isCorrect: false, orderIndex: 1 }];
    setQuestions(prev => [...prev, {
      id: `new-${Date.now()}`,
      questionText: '',
      questionType: type,
      orderIndex: prev.length,
      options: defaultOptions,
    }]);
  };

  const updateQuestion = (idx, field, value) => {
    setQuestions(prev => prev.map((q, i) => i === idx ? { ...q, [field]: value } : q));
  };

  const updateOption = (qIdx, oIdx, field, value) => {
    setQuestions(prev => prev.map((q, i) => {
      if (i !== qIdx) return q;
      const opts = q.options.map((o, j) => {
        if (field === 'isCorrect') return { ...o, isCorrect: j === oIdx };
        return j === oIdx ? { ...o, [field]: value } : o;
      });
      return { ...q, options: opts };
    }));
  };

  const addOption = (qIdx) => {
    setQuestions(prev => prev.map((q, i) => {
      if (i !== qIdx || q.options.length >= 4) return q;
      return { ...q, options: [...q.options, { optionText: '', isCorrect: false, orderIndex: q.options.length }] };
    }));
  };

  const removeOption = (qIdx, oIdx) => {
    setQuestions(prev => prev.map((q, i) => {
      if (i !== qIdx || q.options.length <= 2) return q;
      const opts = q.options.filter((_, j) => j !== oIdx).map((o, j) => ({ ...o, orderIndex: j }));
      // ensure one correct option remains
      if (!opts.some(o => o.isCorrect)) opts[0].isCorrect = true;
      return { ...q, options: opts };
    }));
  };

  const removeQuestion = (idx) => {
    setQuestions(prev => prev.filter((_, i) => i !== idx).map((q, i) => ({ ...q, orderIndex: i })));
  };

  return (
    <div>
      <div className="page-eyebrow" style={{ marginBottom: 4 }}>Quiz editor</div>
      <h2 style={{ fontFamily: 'var(--font-display)', fontWeight: 500, fontSize: 24, letterSpacing: '-0.01em', margin: '0 0 24px' }}>
        {quiz.title || 'Untitled quiz'}
      </h2>

      {/* Quiz metadata */}
      <div className="field">
        <label>Quiz title</label>
        <input className="input" value={titleVal} onChange={e => setTitleVal(e.target.value)} disabled={isLocked} />
      </div>

      <div className="field">
        <label>Description</label>
        <textarea className="input textarea" value={descVal} onChange={e => setDescVal(e.target.value)} rows={3} disabled={isLocked} />
      </div>

      <div className="field">
        <label>Passing score (%)</label>
        <input
          className="input"
          type="number"
          min={0} max={100}
          value={passingScore}
          onChange={e => setPassingScore(e.target.value)}
          disabled={isLocked}
          style={{ maxWidth: 100 }}
        />
      </div>

      {!isLocked && (
        <button className="btn btn-secondary btn-sm" onClick={saveQuiz} disabled={saving} style={{ marginBottom: 32 }}>
          {saving ? 'Saving…' : 'Save quiz settings'}
        </button>
      )}

      {/* Questions */}
      <div style={{ borderTop: '1px solid var(--rule)', paddingTop: 24, marginTop: 8 }}>
        <h3 style={{ fontSize: 15, fontWeight: 600, margin: '0 0 16px' }}>Questions</h3>

        {questions.length === 0 && (
          <p style={{ color: 'var(--ink-3)', fontSize: 14, marginBottom: 16 }}>No questions yet. Add one below.</p>
        )}

        {questions.map((q, qIdx) => (
          <div
            key={q.id ?? qIdx}
            style={{ border: '1px solid var(--rule)', borderRadius: 10, padding: '16px', marginBottom: 12, background: 'var(--surface-2)' }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
              <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--ink-3)', background: 'var(--surface-3)', padding: '2px 8px', borderRadius: 4 }}>
                {q.questionType === 'TRUE_FALSE' ? 'True / False' : 'MCQ'}
              </span>
              <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>Q{qIdx + 1}</span>
              <div style={{ flex: 1 }} />
              {!isLocked && (
                <button
                  style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink-4)', padding: 2 }}
                  onClick={() => removeQuestion(qIdx)}
                  title="Remove question"
                >
                  <Icon name="trash-2" size={13} />
                </button>
              )}
            </div>

            <div className="field" style={{ marginBottom: 10 }}>
              <textarea
                className="input textarea"
                value={q.questionText}
                onChange={e => updateQuestion(qIdx, 'questionText', e.target.value)}
                placeholder="Question text"
                rows={2}
                disabled={isLocked}
                style={{ fontSize: 14 }}
              />
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {q.options.map((opt, oIdx) => (
                <div key={oIdx} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <input
                    type="radio"
                    name={`correct-${q.id ?? qIdx}`}
                    checked={opt.isCorrect}
                    onChange={() => !isLocked && updateOption(qIdx, oIdx, 'isCorrect', true)}
                    disabled={isLocked}
                    title="Mark as correct"
                    style={{ cursor: isLocked ? 'default' : 'pointer', flexShrink: 0 }}
                  />
                  <input
                    className="input"
                    style={{ flex: 1, fontSize: 13, padding: '4px 8px' }}
                    value={opt.optionText}
                    onChange={e => updateOption(qIdx, oIdx, 'optionText', e.target.value)}
                    placeholder={`Option ${oIdx + 1}`}
                    disabled={isLocked || q.questionType === 'TRUE_FALSE'}
                  />
                  {!isLocked && q.questionType === 'MCQ' && q.options.length > 2 && (
                    <button
                      style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink-4)', padding: 2, flexShrink: 0 }}
                      onClick={() => removeOption(qIdx, oIdx)}
                    >
                      <Icon name="x" size={12} />
                    </button>
                  )}
                </div>
              ))}
            </div>

            {!isLocked && q.questionType === 'MCQ' && q.options.length < 4 && (
              <button
                className="btn btn-secondary btn-xs"
                style={{ marginTop: 8 }}
                onClick={() => addOption(qIdx)}
              >
                <Icon name="plus" size={11} /> Add option
              </button>
            )}
          </div>
        ))}

        {!isLocked && (
          <div style={{ display: 'flex', gap: 8, marginBottom: 24 }}>
            <button className="btn btn-secondary btn-sm" onClick={() => addQuestion('MCQ')}>
              <Icon name="circle-dot" size={13} /> Add MCQ
            </button>
            <button className="btn btn-secondary btn-sm" onClick={() => addQuestion('TRUE_FALSE')}>
              <Icon name="check-square" size={13} /> Add True/False
            </button>
          </div>
        )}

        {!isLocked && questions.length > 0 && (
          <button className="btn btn-primary btn-sm" onClick={saveQuestions} disabled={savingQ}>
            {savingQ ? 'Saving…' : 'Save questions'}
          </button>
        )}
      </div>

      {!isLocked && (
        <div style={{ display: 'flex', justifyContent: 'flex-start', borderTop: '1px solid var(--rule)', paddingTop: 18, marginTop: 24 }}>
          <button className="btn btn-danger btn-sm" onClick={deleteQuiz}>
            <Icon name="trash-2" size={14} /> Delete quiz
          </button>
        </div>
      )}

      {toast && (
        <div style={{
          position: 'fixed', bottom: 24, left: '50%', transform: 'translateX(-50%)',
          background: 'var(--ink)', color: '#fff', padding: '10px 20px',
          borderRadius: 8, fontSize: 14, zIndex: 9999,
        }}>
          {toast}
        </div>
      )}
    </div>
  );
}
