import javax.xml.namespace.QName
import javax.xml.stream.events.Characters

class Element(val name: String) {
    var value: String = ""
    var child: Element? = null
    var sibling: Element? = null
    private val attributeList = ArrayList<Pair<String, String>>()
    val attributes: List<Pair<String, String>>
        get() {
            return List(attributeList.size) { i -> attributeList[i] }
        }

    fun clearAttributes() {
        attributeList.clear()
    }

    constructor(qName: QName) : this(qName.toString())

    fun addChild(c: Element) {
        if (child == null) {
            child = c
        } else {
            var target = child
            if (target != null) {
                while (target!!.sibling != null)
                    target = target.sibling
            }
            target?.sibling = c
        }
    }

    fun getNamedAttribute(name: String) : String? {
        return attributeList.find { it.first == name }?.second
    }

    private fun addAttribute(name: String, value: String) {
        attributeList.add(Pair(name, value))
    }

    fun setAttribute(name: String, value: String) {
        val item = attributeList.find { it.first == name }
        if (item != null)
            attributeList.remove(item)
        addAttribute(name, value)
    }

    fun append(chars: Characters) {
        if (chars.isIgnorableWhiteSpace)
            return
        value += chars.toString().trim()
    }

    override fun toString(): String {
        return "Element(\"$name\", \"$value\", nAttr=${attributeList.size})"
    }
}