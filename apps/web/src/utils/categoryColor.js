const GRADIENTS = [
  // CS / Programming / Algorithms
  [/comput|programm|\bcs\b|algorithm|data struct|software/i,
    'linear-gradient(135deg,#1F2160 0%,#4144A6 65%,rgba(232,89,62,.32) 100%)'],
  // Design / UI / UX
  [/design|ui\b|ux\b|figma|layout|typograph|visual/i,
    'linear-gradient(135deg,#2D1A48 0%,#7B35B0 65%,rgba(232,89,62,.28) 100%)'],
  // Writing / Communication / Language
  [/writ|communicat|language|english|content/i,
    'linear-gradient(135deg,#3D1805 0%,#B05510 65%,rgba(228,174,62,.38) 100%)'],
  // Data / Science / Analytics
  [/\bdata\b|analytic|scienc|statist|machine learn|ml\b|ai\b/i,
    'linear-gradient(135deg,#092B38 0%,#146690 65%,rgba(45,210,175,.28) 100%)'],
  // Business / Marketing / Finance / Product
  [/business|market|financ|startup|product|manag/i,
    'linear-gradient(135deg,#182D0E 0%,#2A6B1A 65%,rgba(100,210,65,.22) 100%)'],
];

const DEFAULT = 'linear-gradient(135deg,#1F2160 0%,#4144A6 65%,rgba(232,89,62,.22) 100%)';

export function categoryGradient(category = '') {
  for (const [regex, gradient] of GRADIENTS) {
    if (regex.test(category)) return gradient;
  }
  return DEFAULT;
}