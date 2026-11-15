import { FieldValue, Timestamp } from 'firebase-admin/firestore';
import {
  isEncodedTimestamp,
  isServerTimestamp,
  timestampFromParts,
  timestampParts,
} from '../encoding.js';

/** Contract encoding -> Admin SDK values (`{__ts}` -> Timestamp, `{__sentinel}` -> serverTimestamp()). */
export function toFirestore(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(toFirestore);
  if (value === null || typeof value !== 'object') return value;
  if (isServerTimestamp(value)) return FieldValue.serverTimestamp();
  if (isEncodedTimestamp(value)) {
    const { seconds, nanoseconds } = timestampParts(value);
    return new Timestamp(seconds, nanoseconds);
  }
  return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, toFirestore(v)]));
}

/** Admin SDK values -> contract encoding (Timestamp -> `{__ts}`). */
export function fromFirestore(value: unknown): unknown {
  if (value instanceof Timestamp) return timestampFromParts(value.seconds, value.nanoseconds);
  if (Array.isArray(value)) return value.map(fromFirestore);
  if (value === null || typeof value !== 'object') return value;
  return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, fromFirestore(v)]));
}
