package com.resumeiq.jobdescription.parse;

/**
 * One headed block of a job posting.
 *
 * @param section what kind of block this is
 * @param heading the heading as the poster wrote it, or null for text that appeared before any
 *                heading. Kept in its original capitalisation because it is shown back to the
 *                user as evidence — "found under: Nice to have" is far more convincing than a
 *                bare label.
 * @param text    the body, already normalised by
 *                {@link com.resumeiq.common.text.PlainText}, with the heading line removed
 */
public record PostingBlock(PostingSection section, String heading, String text) {

    /** What a skill found in this block means to the posting. */
    public SkillImportance importance() {
        return section.importance();
    }

    /** The same block, reclassified. Used when an unstructured posting is read as requirements. */
    public PostingBlock as(PostingSection other) {
        return new PostingBlock(other, heading, text);
    }
}
