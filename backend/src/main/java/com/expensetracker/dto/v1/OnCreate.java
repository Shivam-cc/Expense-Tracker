package com.expensetracker.dto.v1;

/**
 * Validation group for fields that are required only when a resource is created (POST). PUT validates the
 * default group only, so a {@code clientId} sent on update is ignored.
 */
public interface OnCreate {
}
