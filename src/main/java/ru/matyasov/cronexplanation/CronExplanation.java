package ru.matyasov.cronexplanation;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.*;

public class CronExplanation {

//    Список месяцев с русскими названиями в именительном и родительном падежах
    private static final String[][] MONTHS = new String[][]{
            {"JAN", "январь", "января"},
            {"FEB", "февраль", "февраля"},
            {"MAR", "март", "марта"},
            {"APR", "апрель", "апреля"},
            {"MAY", "май", "мая"},
            {"JUN", "июнь", "июня"},
            {"JUL", "июль", "июля"},
            {"AUG", "август", "августа"},
            {"SEP", "сентябрь", "сентября"},
            {"OCT", "октябрь", "октября"},
            {"NOV", "ноябрь", "ноября"},
            {"DEC", "декабрь", "декабря"}
    };

    //    Список дней недели с русскими названиями в именительном и родительном падежах
    private static final String[][] DAYS_OF_WEEK = new String[][]{
            {"MON", "понедельник", "понедельника"},
            {"TUE", "вторник", "вторника"},
            {"WED", "среда", "среды"},
            {"THU", "четверг", "четверга"},
            {"FRI", "пятница", "пятницы"},
            {"SAT", "суббота", "субботы"},
            {"SUN", "воскресенье", "воскресенья"}
    };

//    Род названий дней недели
    private static final String[] DAYS_OF_WEEK_GENUS = new String[]{
            "M",
            "M",
            "F",
            "M",
            "F",
            "F",
            "N"
    };

//    Объяснения для распарсенных макросов
    private static final HashMap<String, String> PARSED_MACRO_EXPLANATIONS = new HashMap<>(){{
        put("0 0 0 1 1 *", "Ежегодно, 1 января, в полночь");
        put("0 0 0 1 * *", "Ежемесячно, 1 числа, в полночь");
        put("0 0 0 * * 0", "Еженедельно, в воскресенье, в полночь");
        put("0 0 0 * * *", "Ежедневно, в полночь");
        put("0 0 * * * *", "Ежечасно, в 00:00");
    }};

//    Типы полей
    private enum FieldType {
        SECONDS("секунда",59, 'F'),
        MINUTES("минута", 59, 'F'),
        HOURS("час",23, 'M'),
        DAYS_OF_MONTH("день месяца",31, 'M'),
        MONTHS("месяц",12, 'M'),
        DAYS_OF_WEEK("день недели",7, 'M');

        private final String nameSingular;

        private final int maxValue;

        private final char genus;

        FieldType(String nameSingular, int maxValue, char genus) {
            this.nameSingular = nameSingular;
            this.maxValue = maxValue;
            this.genus = genus;
        }

    }

    private enum Case {

        NOMINATIVE,
        GENITIVE

    }



//    Главный метод
    public static String explain(String string) {


//        Проверяем строку на валидность, если валидный cron, то вычисляем, иначе возвращаем сообщение об ошибке
        if (CronExpression.isValidExpression(string)) {

            String cronExpression = CronExpression.parse(string).toString();

            StringBuilder result = new StringBuilder();

//            Если был передан макрос или соответсвующий макросу cron, то вернём описание для макроса
            if (PARSED_MACRO_EXPLANATIONS.containsKey(cronExpression)) {

                result.append(PARSED_MACRO_EXPLANATIONS.get(cronExpression));

            } else {

//                Разбиваем строку на массив полей
                String[] fields = StringUtils.tokenizeToStringArray(cronExpression, " ");

//                Многопоточность - просто так...
                ExecutorService executorService = Executors.newFixedThreadPool(3);

//                Считаем по каждому полю выражения
                Future<String> seconds = executorService.submit(
                        () -> "По секундам (" + fields[0] + "): " + explainField(fields[0], FieldType.SECONDS));

                Future<String> minutes = executorService.submit(
                        () -> "По минутам (" + fields[1] + "): " + explainField(fields[1], FieldType.MINUTES));

                Future<String> hours = executorService.submit(
                        () -> "По часам (" + fields[2] + "): " + explainField(fields[2], FieldType.HOURS));

                Future<String> daysOfMonth = executorService.submit(
                        () -> "По дням месяца (" + fields[3] + "): " + explainField(fields[3], FieldType.DAYS_OF_MONTH));

                Future<String> months = executorService.submit(
                        () -> "По месяцам (" + fields[4] + "): " + explainField(fields[4], FieldType.MONTHS));

                Future<String> daysOfWeek = executorService.submit(
                        () -> "По дням недели (" + fields[5] + "): " + explainField(fields[5], FieldType.DAYS_OF_WEEK));

                try {

//                    Формируем результат
                    result.append(seconds.get());

                    result.append(minutes.get());

                    result.append(hours.get());

                    result.append(daysOfMonth.get());

                    result.append(months.get());

                    result.append(daysOfWeek.get());

                } catch (InterruptedException | ExecutionException e) {

                    return e.getMessage();

                } finally {

                    executorService.shutdown();

                }

            }

            return result.toString();

        } else {

            return "Не правильный формат cron.";

        }

    }

    private static String explainField(String field, FieldType fieldType) {

        String result;

//        Для этих полей одинаковые правила
        if (fieldType.equals(FieldType.SECONDS) || fieldType.equals(FieldType.MINUTES) || fieldType.equals(FieldType.HOURS)) {

            result = explainSecondsMinutesHours(field, fieldType);

//        Дни месяца
        } else if (fieldType.equals(FieldType.DAYS_OF_MONTH)) {

            result = explainDaysOfMonth(field);

//        Месяцы
        } else if (fieldType.equals(FieldType.MONTHS)) {

            result = explainMonths(field);

//        Остались только дни недели
        } else {

            result = explainDaysOfWeek(field);

        }

        return result + "\n";

    }
    private static String explainSecondsMinutesHours(String field, FieldType fieldType) {

        String[] values = StringUtils.tokenizeToStringArray(field, ",");

//        Обрабатываем каждое значение массива
        for (int i = 0; i < values.length; i++) {

//            Если содержит не цифровой символ, то содержит диапазон или интервал
            if (values[i].matches(".*\\D.*")) {

                values[i] = calculateRange(values[i], fieldType);

            }

        }

    return StringUtils.collectionToDelimitedString(Arrays.stream(values).toList(), ", ");

    }

    private static String explainDaysOfMonth(String field) {

//        "?" и "*" имеют одинаковое назначение, сразу переведём в верхний регистр
        field = field.replace("?", "*").toUpperCase();

        String[] values = StringUtils.tokenizeToStringArray(field, ",");

//        Обрабатываем каждое значение массива
        for (int i = 0; i < values.length; i++) {

//            Обработка значения "L"
            if (values[i].equals("L")) {

                values[i] = "последний день месяца";

//            Обработка значения "L-n"
            } else if (values[i].startsWith("L-")) {

                values[i] = "за " + values[i].substring(2) + " до конца месяца";

//            Обработка значения "LW"
            } else if (values[i].equals("LW")) {

                values[i] = "последний рабочий день месяца";


//            Обработка значения "nW"
            } else if (values[i].endsWith("W")) {

                values[i] = "ближайший рабочий день к " + values[i].replace("W", "") + "-му дню месяца";

//            Если содержит не цифровой символ, то содержит диапазон или интервал
            } else if (values[i].matches(".*\\D.*")) {

                values[i] = calculateRange(values[i], FieldType.DAYS_OF_MONTH);

            }

        }

        return StringUtils.collectionToDelimitedString(Arrays.stream(values).toList(), ", ");

    }

    private static String explainMonths(String field) {

        String[] values = StringUtils.tokenizeToStringArray(field, ",");

//        Обрабатываем каждое значение массива
        for (int i = 0; i < values.length; i++) {


//            Могут быть текстовыми обозначениями, заменим текстовые обозначения на порядковые номера
            values[i] = replaceWithOrdinals(values[i], FieldType.MONTHS);

//            Если содержит не цифровой символ, то содержит диапазон или интервал
            if (values[i].matches(".*\\D.*")) {

                values[i] = calculateRange(values[i], FieldType.MONTHS);

//            Порядковые номера заменяем на название в именительном падеже (для диапазонов это сделано в calculateRange)
            } else {

                values[i] = replaceWithName(values[i], FieldType.MONTHS, Case.NOMINATIVE);

            }

        }

        return StringUtils.collectionToDelimitedString(Arrays.stream(values).toList(), ", ");

    }

    private static String explainDaysOfWeek(String field) {

//        "?" и "*" имеют одинаковое назначение, сразу переведём в верхний регистр
        field = field.replace("?", "*").toUpperCase();

        String[] values = StringUtils.tokenizeToStringArray(field, ",");

//        Обрабатываем каждое значение массива
        for (int i = 0; i < values.length; i++) {

//            Могут быть текстовыми обозначениями, заменим текстовые обозначения на порядковые номера
            values[i] = replaceWithOrdinals(values[i], FieldType.DAYS_OF_WEEK);

//            Обработка значения "dL" (выше "DDDL" пересчитано в "dL")
            if (values[i].endsWith("L")) {

                int integerValue = Integer.parseInt(values[i].replace("L", ""));

                String genus = DAYS_OF_WEEK_GENUS[integerValue - 1];

                values[i] = (genus.equals("M") ? "последний " : genus.equals("F") ? "последняя " : "последнее ")
                        + DAYS_OF_WEEK[integerValue - 1][1] + " месяца";

//            Обработка значения "d#n" (выше "DDD#n" пересчитано в "d#n")
            } else if (values[i].contains("#")) {

//                Порядковый номер дня недели
                String dayNumber = values[i].substring(0, values[i].indexOf("#"));

//                Порядковый номер dayNumber
                String number = values[i].substring(values[i].indexOf("#") + 1);

                String genus = DAYS_OF_WEEK_GENUS[Integer.parseInt(dayNumber) - 1];

                values[i] = number + (genus.equals("M") ? "-й " : genus.equals("F") ? "-я " : "-е ")
                        + replaceWithName(dayNumber, FieldType.DAYS_OF_WEEK, Case.NOMINATIVE) + " месяца";

//            Если содержит не цифровой символ, то содержит диапазон или интервал
            } else if (values[i].matches(".*\\D.*")) {

                values[i] = calculateRange(values[i], FieldType.DAYS_OF_MONTH);

            }

        }

        return StringUtils.collectionToDelimitedString(Arrays.stream(values).toList(), ", ");

    }

    //    Замена текстовых значений на номера для месяцев и дней недели
    private static String replaceWithOrdinals(String value, FieldType fieldType) {

        value = value.toUpperCase();

        String result;

        if (fieldType.equals(FieldType.MONTHS)) {

            for (int i = 0; i < MONTHS.length; i++) {

                value = value.replace(MONTHS[i][0], Integer.toString(i + 1));

            }

            result = value;

        } else if (fieldType.equals(FieldType.DAYS_OF_WEEK)) {

            for (int i = 0; i < DAYS_OF_WEEK.length; i++) {

                value = value.replace(DAYS_OF_WEEK[i][0], Integer.toString(i + 1));

            }

            result = value;

        } else {

            result = "";

        }

        return result;

    }

//    Вычисление объяснения диапазона
    private static String calculateRange(String element, FieldType fieldType) {

        String rangeFrom = "0";

        String rangeTo = "0";

//        Окончание диапазона, если начинается "*"
        if (element.startsWith("*")) {

            rangeTo = String.valueOf(fieldType.maxValue);

//        Начало и окончание диапазона, если содержит "-"
        } else if (element.contains("-")) {

            String range = element.contains("/") ? element.substring(0, element.indexOf("/")) : element;

            rangeFrom = range.substring(0, range.indexOf("-"));

            rangeTo = range.substring(range.indexOf("-") + 1);

//        Если на текущий момент содержит "/", то перед "/" - одно число
        } else if (element.contains("/")) {

            rangeFrom = element.substring(0, element.indexOf("/"));

            rangeTo = rangeFrom;

        }

//        Замена на названия для месяцев и дней недели
        if (fieldType.equals(FieldType.MONTHS) || fieldType.equals(FieldType.DAYS_OF_WEEK)) {

            // Родительный падеж
            rangeFrom = replaceWithName(rangeFrom, fieldType, Case.GENITIVE);

            // Именительный падеж
            rangeTo = replaceWithName(rangeTo, fieldType, Case.NOMINATIVE);

        }

        return (fieldType.genus == 'F' ? "каждая " : "каждый ")
                + (element.contains("/") ? element.substring(element.indexOf("/") + 1) + (fieldType.genus == 'F' ? "-я " : "-й "): "")
                + fieldType.nameSingular + " с " + rangeFrom + " по " + rangeTo + " (включительно)";

    }



//    Замена числового значения на название для месяцев и дней недели
    private static String replaceWithName(String value, FieldType fieldType, Case c) {

//        Первый индекс двумерного массива
//        Значение "0" в днях недели - воскресенье
        int firstIndex = Integer.parseInt(value) > 0 ? Integer.parseInt(value) - 1 : 6;

//        Второй индекс двумерного массива
        int secondIndex = c.equals(Case.NOMINATIVE) ? 1 : c.equals(Case.GENITIVE) ? 2 : 0;

//        Если не месяц или день недели, то value
        return fieldType.equals(FieldType.MONTHS) ? MONTHS[firstIndex][secondIndex] : fieldType.equals(FieldType.DAYS_OF_WEEK) ? DAYS_OF_WEEK[firstIndex][secondIndex] : value;

    }

}