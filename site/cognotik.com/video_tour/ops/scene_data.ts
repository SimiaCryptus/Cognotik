// ============================================================
// Video Editing Plan Schema
// ============================================================

/** Timecode in "HH:MM:SS.mmm" or "M:SS" or "M:SS.mmm" format */
type Timecode = string;

/** A time range within the source video */
interface TimeRange {
  start: Timecode;
  end: Timecode;
}

// ------------------------------------------------------------
// Edit Actions
// ------------------------------------------------------------

type EditAction =
  | "keep"
  | "cut"
  | "trim"
  | "time-compress"
  | "time-compress-and-mute"
  | "tighten"
  | "edit"; // dialogue cleanup / partial rework

interface EditDecision {
  /** Source timecode range this decision applies to */
  source: TimeRange;

  /** Primary action to take */
  action: EditAction;

  /**
   * Some segments have a compound action, e.g. "KEEP with time-compression".
   * The secondary action applies to a sub-range or is layered on top.
   */
  secondaryAction?: EditAction;

  /** Sub-range within `source` that the secondary action targets */
  secondaryRange?: TimeRange;

  /** Human-readable rationale / description of what happens here */
  notes: string;

  /** The transcript dialogue in this range (if relevant) */
  dialogue?: string;

  /**
   * For time-compress actions: what the compressed duration should be.
   * e.g. "~4s", "~10-12s"
   */
  compressedDuration?: string;

  /** Suggested speed multiplier for time-compress, e.g. 3, 4, 8 */
  speedMultiplier?: number;

  /** Whether audio should be muted during this segment */
  muteAudio?: boolean;

  /** Text overlay to show during this segment (e.g. "Generating...") */
  overlay?: string;
}

// ------------------------------------------------------------
// Scenes / Segments
// ------------------------------------------------------------

interface Segment {
  /** Segment number (1-based) */
  index: number;

  /** Descriptive title, e.g. "Introduction to the App" */
  title: string;

  /** Overall source range this segment spans */
  source: TimeRange;

  /** Ordered list of edit decisions within this segment */
  edits: EditDecision[];
}

// ------------------------------------------------------------
// Transitions & Billboards
// ------------------------------------------------------------

type TransitionType =
  | "fade-in-from-black"
  | "fade-to-black"
  | "crossfade"
  | "dissolve"
  | "speed-ramp"
  | "match-cut";

interface Billboard {
  /** e.g. "intro" or "outro" */
  placement: "intro" | "outro";

  /** Main title text on the card */
  title: string;

  /** Optional subtitle / tagline */
  subtitle?: string;

  /** Additional text lines (e.g. links, CTAs) */
  additionalText?: string[];

  /** How long the card is displayed */
  holdDuration: string;

  /** Transition into the card */
  transitionIn: TransitionType;

  /** Transition out of the card */
  transitionOut: TransitionType;
}

interface TransitionNote {
  /** Where this transition applies */
  context: string;

  /** Type of transition */
  type: TransitionType;

  /** Duration of the transition effect */
  duration?: string;

  /** Additional details */
  notes?: string;
}

// ------------------------------------------------------------
// Intro / Outro
// ------------------------------------------------------------

interface IntroSection {
  /** Source timecode range to trim (dead air, etc.) */
  trimRange?: TimeRange;

  /** Billboard card to insert */
  billboard: Billboard;

  /** Any additional notes about the intro */
  notes?: string;
}

interface OutroSection {
  /** Source timecode after which to begin outro */
  startAfter: Timecode;

  /** Source range to trim (trailing silence, etc.) */
  trimRange?: TimeRange;

  /** Billboard card to insert */
  billboard: Billboard;

  /** Any additional notes about the outro */
  notes?: string;
}

// ------------------------------------------------------------
// Edit Summary (the table at the bottom of each plan)
// ------------------------------------------------------------

interface EditSummaryEntry {
  editType: string;
  timestamp: string;
  durationSaved?: string;
  notes: string;
}

// ------------------------------------------------------------
// Polish & Post-Production
// ------------------------------------------------------------

interface AudioNotes {
  /** Whether to normalize speech levels */
  normalizeLevels: boolean;

  /** Whether to add background music */
  backgroundMusic: boolean;

  /** Details about music placement */
  musicNotes?: string;

  /** Noise reduction instructions */
  noiseReduction?: string;

  /** Any other audio notes */
  other?: string[];
}

interface PolishNotes {
  /** Transition guidelines for the whole video */
  transitions: TransitionNote[];

  /** Audio post-production notes */
  audio: AudioNotes;

  /** Whether to add captions/subtitles */
  captions?: boolean;

  /** Lower-third / callout suggestions */
  lowerThirds?: string[];

  /** Any other polish notes */
  other?: string[];
}

// ------------------------------------------------------------
// Duration Estimates
// ------------------------------------------------------------

interface DurationEstimate {
  /** Original source duration, e.g. "~7:53" */
  original: string;

  /** Estimated final duration after edits, e.g. "~3:30 - 4:00" */
  estimated: string;
}

// ------------------------------------------------------------
// Top-Level Plan
// ------------------------------------------------------------

interface VideoEditingPlan {
  /** Video / project title */
  title: string;

  /** Brief description of the video content */
  overview: string;

  /** Source material references */
  source: {
    transcript: string;
    totalDuration: string;
    /** Any other source files */
    other?: string[];
  };

  /** Intro section definition */
  intro: IntroSection;

  /** Ordered list of content segments */
  segments: Segment[];

  /** Outro section definition */
  outro: OutroSection;

  /** Summary table of all edits */
  editSummary: EditSummaryEntry[];

  /** Polish & post-production guidelines */
  polish: PolishNotes;

  /** Duration estimates */
  duration: DurationEstimate;

  /**
   * Optional: final video structure outline
   * (the numbered list some plans include at the end)
   */
  finalStructure?: FinalStructureEntry[];
}

interface FinalStructureEntry {
  /** Approximate timecode range in the final video */
  timeRange: string;

  /** Description of what appears in this portion */
  description: string;
}

// ============================================================
// Export
// ============================================================

export type {
  Timecode,
  TimeRange,
  EditAction,
  EditDecision,
  Segment,
  TransitionType,
  Billboard,
  TransitionNote,
  IntroSection,
  OutroSection,
  EditSummaryEntry,
  AudioNotes,
  PolishNotes,
  DurationEstimate,
  FinalStructureEntry,
  VideoEditingPlan,
};
