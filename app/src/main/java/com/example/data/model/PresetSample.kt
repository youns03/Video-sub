package com.example.data.model

data class PresetSample(
    val title: String,
    val platform: PlatformType,
    val url: String,
    val durationSeconds: Int,
    val description: String,
    val sampleSegments: List<SubtitleSegment>
)

object PresetSamples {
    val list = listOf(
        PresetSample(
            title = "ريبورتاج: صناعة الخبز الباريسي التقليدي (Reel)",
            platform = PlatformType.FACEBOOK,
            url = "https://www.facebook.com/reel/1083920194827110",
            durationSeconds = 28,
            description = "مقطع فيسبوك ريلز بالفرنسية يستعرض أسرار وتاريخ صناعة الخبز الفرنسي التراثي",
            sampleSegments = listOf(
                SubtitleSegment(
                    id = 1,
                    startTimeMs = 0,
                    endTimeMs = 3800,
                    frenchText = "Bonjour à tous, aujourd'hui nous découvrons les secrets du pain artisanal parisien.",
                    arabicText = "مرحباً بالجميع، نكتشف اليوم أسرار صناعة الخبز الباريسي الحرفي التقليدي.",
                    speaker = "المقدم"
                ),
                SubtitleSegment(
                    id = 2,
                    startTimeMs = 4200,
                    endTimeMs = 8600,
                    frenchText = "Tout commence par le choix d'une farine biologique et une fermentation lente.",
                    arabicText = "يبدأ كل شيء باختيار دقيق عضوي عالي الجودة مع عملية تخمير بطيئة.",
                    speaker = "الخباز"
                ),
                SubtitleSegment(
                    id = 3,
                    startTimeMs = 9000,
                    endTimeMs = 14200,
                    frenchText = "Cette méthode traditionnelle permet d'obtenir une croûte dorée et croustillante.",
                    arabicText = "تسمح هذه الطريقة التراثية بالحصول على قشرة ذهبية مقرمشة ولذيذة.",
                    speaker = "الخباز"
                ),
                SubtitleSegment(
                    id = 4,
                    startTimeMs = 14800,
                    endTimeMs = 20500,
                    frenchText = "En France, la baguette est désormais inscrite au patrimoine mondial de l'UNESCO.",
                    arabicText = "في فرنسا، أصبحت الباغيت مدرجة رسميًا ضمن قائمة التراث العالمي لليونسكو.",
                    speaker = "المقدم"
                ),
                SubtitleSegment(
                    id = 5,
                    startTimeMs = 21000,
                    endTimeMs = 27500,
                    frenchText = "Une véritable fierté pour nos artisans passionnés qui perpétuent ce savoir-faire.",
                    arabicText = "إنه مبعث فخر حقيقي لحرفيينا المخلصين الذين يواصلون نقل هذه المهارة الأصيلة.",
                    speaker = "المقدم"
                )
            )
        ),
        PresetSample(
            title = "تقرير بيئي: الطاقات المتجددة ومستقبل الكوكب",
            platform = PlatformType.FACEBOOK,
            url = "https://www.facebook.com/watch/?v=982147302194821",
            durationSeconds = 34,
            description = "فيديو وثائقي فرنسي حول التغير المناخي والحلول البيئية المستدامة",
            sampleSegments = listOf(
                SubtitleSegment(
                    id = 1,
                    startTimeMs = 500,
                    endTimeMs = 5200,
                    frenchText = "La transition écologique est devenue le défi majeur de notre siècle.",
                    arabicText = "أصبح التحول البيئي هو التحدي الأكبر والأهم في عصرنا الحالي.",
                    speaker = "الراوي"
                ),
                SubtitleSegment(
                    id = 2,
                    startTimeMs = 5600,
                    endTimeMs = 11300,
                    frenchText = "L'énergie solaire et l'énergie éolienne connaissent une croissance spectaculaire.",
                    arabicText = "تشهد الطاقة الشمسية وطاقة الرياح نمواً سريعاً ومذهلاً في كل أنحاء العالم.",
                    speaker = "الراوي"
                ),
                SubtitleSegment(
                    id = 3,
                    startTimeMs = 12000,
                    endTimeMs = 18400,
                    frenchText = "De nombreuses villes européennes investissent massivement dans les transports propres.",
                    arabicText = "تستثمر العديد من المدن الأوروبية استثمارات ضخمة في وسائل النقل النظيف.",
                    speaker = "خبيرة بيئية"
                ),
                SubtitleSegment(
                    id = 4,
                    startTimeMs = 19000,
                    endTimeMs = 25800,
                    frenchText = "Mais pour réussir, chaque citoyen doit adopter des habitudes plus durables.",
                    arabicText = "ولكن للنجاح، يتعين على كل فرد تبني عادات يومية أكثر استدامة وصداقة للبيئة.",
                    speaker = "خبيرة بيئية"
                ),
                SubtitleSegment(
                    id = 5,
                    startTimeMs = 26500,
                    endTimeMs = 33200,
                    frenchText = "L'avenir de notre planète dépend des décisions que nous prenons aujourd'hui.",
                    arabicText = "إن مستقبل كوكبنا مرهون بالقرارات التي نتخذها اليوم معاً.",
                    speaker = "الراوي"
                )
            )
        ),
        PresetSample(
            title = "حوار تقني: أسرار الابتكار والذكاء الاصطناعي",
            platform = PlatformType.YOUTUBE,
            url = "https://www.youtube.com/watch?v=fr_tech_inno_2026",
            durationSeconds = 30,
            description = "مقابلة حول أحدث التقنيات والبرمجيات وكيفية الاستفادة منها",
            sampleSegments = listOf(
                SubtitleSegment(
                    id = 1,
                    startTimeMs = 0,
                    endTimeMs = 4500,
                    frenchText = "Dans cette édition spéciale, nous recevons des chercheurs en intelligence artificielle.",
                    arabicText = "في هذا العدد الخاص، نستضيف نخبة من الباحثين في مجال الذكاء الاصطناعي.",
                    speaker = "المحاور"
                ),
                SubtitleSegment(
                    id = 2,
                    startTimeMs = 5000,
                    endTimeMs = 10800,
                    frenchText = "Comment ces nouveaux outils transforment-ils l'apprentissage des langues étrangères ?",
                    arabicText = "كيف تعمل هذه الأدوات الجديدة على تغيير طريقة تعلّم اللغات الأجنبية وترجمتها؟",
                    speaker = "المحاور"
                ),
                SubtitleSegment(
                    id = 3,
                    startTimeMs = 11500,
                    endTimeMs = 17900,
                    frenchText = "Grâce au traitement automatique du langage naturel, la précision a fait un bond immense.",
                    arabicText = "بفضل معالجة اللغة الطبيعية، حققت دقة الترجمة قفزة نوعية هائلة وغير مسبوقة.",
                    speaker = "الباحث"
                ),
                SubtitleSegment(
                    id = 4,
                    startTimeMs = 18500,
                    endTimeMs = 24200,
                    frenchText = "Nous sommes désormais capables de traduire la voix en temps quasi réel.",
                    arabicText = "أصبحنا الآن قادرين على ترجمة الصوت المنطوق في وقت شبه فوري وبسلاسة تامة.",
                    speaker = "الباحث"
                ),
                SubtitleSegment(
                    id = 5,
                    startTimeMs = 25000,
                    endTimeMs = 29800,
                    frenchText = "Ce qui ouvre des perspectives passionnantes pour la communication mondiale.",
                    arabicText = "وهذا ما يفتح آفاقاً مشوقة للغاية للتواصل الإنساني والتبادل الثقافي حول العالم.",
                    speaker = "المحاور"
                )
            )
        )
    )
}
