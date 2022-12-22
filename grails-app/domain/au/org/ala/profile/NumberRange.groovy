package au.org.ala.profile

class NumberRange {
    Double from
    Double to
    boolean fromInclusive = true
    boolean toInclusive = true

    static constraints = {
        from validator: { val, NumberRange range->
            val < range.to
        }
    }
}
