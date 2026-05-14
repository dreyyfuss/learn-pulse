const MESSAGES = {
  NOT_FOUND:              'The resource could not be found.',
  ALREADY_ENROLLED:       'You are already enrolled in this course.',
  ENROLMENT_CODE_INVALID: 'That enrolment code is invalid.',
  COURSE_LOCKED:          'This course has already been started and its content is now locked.',
  COURSE_NOT_PUBLISHABLE: 'The course needs at least one module with one lesson before it can be published.',
  NOT_OWNER:              "You don't have permission to edit this course.",
  LESSON_OUT_OF_ORDER:    'Complete the previous lessons before moving on.',
  MODULE_LOCKED:          "This module isn't unlocked yet — finish the previous module first.",
  ACCESS_DENIED:          "You don't have permission to do that.",
  VALIDATION_ERROR:       'Some fields are invalid — please check your input.',
  INTERNAL_ERROR:         'Something went wrong on our end. Please try again.',
  INVALID_TOKEN:          'Your session has expired. Please sign in again.',
  INVALID_CREDENTIALS:    'Invalid email or password.',
  EMAIL_TAKEN:            'An account with that email already exists.',
  USER_NOT_FOUND:         'User not found.',
  ACCOUNT_SUSPENDED:      'Your account has been suspended. Please contact support.',
  USER_SUSPENDED:         "This user's account is suspended.",
};

export function getErrorMessage(err) {
  const code = err?.data?.error?.code;
  return MESSAGES[code] ?? 'An unexpected error occurred. Please try again.';
}

export default MESSAGES;