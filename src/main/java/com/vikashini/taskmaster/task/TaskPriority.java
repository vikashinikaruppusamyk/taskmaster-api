package com.vikashini.taskmaster.task;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/**
 * Stored as its level (1-3) rather than its name, so that sorting by priority orders
 * LOW, MEDIUM, HIGH instead of alphabetically.
 */
public enum TaskPriority {
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    private final short level;

    TaskPriority(int level) {
        this.level = (short) level;
    }

    public short level() {
        return level;
    }

    static TaskPriority ofLevel(short level) {
        return Arrays.stream(values())
                .filter(p -> p.level == level)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown priority level " + level));
    }

    @Converter(autoApply = true)
    static class LevelConverter implements AttributeConverter<TaskPriority, Short> {

        @Override
        public Short convertToDatabaseColumn(TaskPriority priority) {
            return priority == null ? null : priority.level();
        }

        @Override
        public TaskPriority convertToEntityAttribute(Short level) {
            return level == null ? null : ofLevel(level);
        }
    }
}
