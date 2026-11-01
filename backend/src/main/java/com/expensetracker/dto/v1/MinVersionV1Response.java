package com.expensetracker.dto.v1;

/** Lowest mobile app version the server still supports, per platform. */
public record MinVersionV1Response(String android, String ios) {
}
